import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
IMAGE = '123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/jikchin-app:new'
OLD_IMAGE = '123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/jikchin-app:old'
spec = importlib.util.spec_from_file_location('dispatch_deploy', ROOT / 'scripts/dispatch_deploy.py')
dispatch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dispatch)


class DispatchTest(unittest.TestCase):
    def test_sends_image_and_waits_for_success(self):
        sent = subprocess.CompletedProcess([], 0, json.dumps({'Command': {'CommandId': 'abc'}}), '')
        pending = subprocess.CompletedProcess([], 0, json.dumps({'Status': 'InProgress'}), '')
        success = subprocess.CompletedProcess([], 0, json.dumps({'Status': 'Success'}), '')
        with mock.patch.dict(os.environ, {'EC2_INSTANCE_ID': 'i-1234567890abcdef0', 'IMAGE_URI': IMAGE}), \
             mock.patch.object(dispatch, 'aws', side_effect=[sent, pending, success]) as aws, \
             mock.patch.object(dispatch.time, 'sleep'):
            dispatch.main()
        self.assertEqual(aws.call_count, 3)
        self.assertIn(IMAGE, aws.call_args_list[0].args[-1])
        self.assertIn('get-command-invocation', aws.call_args_list[2].args)

    def test_failed_ssm_command_fails_deployment(self):
        sent = subprocess.CompletedProcess([], 0, json.dumps({'Command': {'CommandId': 'abc'}}), '')
        failed = subprocess.CompletedProcess([], 0, json.dumps({'Status': 'Failed'}), '')
        with mock.patch.dict(os.environ, {'EC2_INSTANCE_ID': 'i-1234567890abcdef0', 'IMAGE_URI': IMAGE}), \
             mock.patch.object(dispatch, 'aws', side_effect=[sent, failed]):
            with self.assertRaisesRegex(RuntimeError, 'Failed'):
                dispatch.main()


@unittest.skipUnless(os.name == 'posix', 'Deployment shell tests require Linux tools')
class DeployTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        self.deploy = self.root / 'deploy'
        self.deploy.mkdir()
        self.env_file = self.deploy / '.env.prod'
        self.env_file.write_text(f"APP_IMAGE={OLD_IMAGE}\nDB_PASSWORD='keep$secret'\n")
        (self.deploy / 'compose.prod.yaml').write_text('previous compose\n')
        self.candidate = self.root / 'candidate.yaml'
        self.candidate.write_text('candidate compose\n')
        self.calls = self.root / 'calls'
        self.executable('aws', 'echo token')
        self.executable('docker', '''
echo "$APP_IMAGE $*" >> "$CALLS"
case "$*" in
  *"ps --all --quiet app"*) echo container ;;
  *"inspect --format {{.Config.Image}}"*) echo "$OLD_IMAGE" ;;
  *"inspect --format {{.State.Running}}"*) echo true ;;
  *"pull app"*) [[ "${FAIL_PULL:-0}" != 1 ]] ;;
  *"port app 8080"*) echo 127.0.0.1:8080 ;;
  *"login "*) cat >/dev/null ;;
esac
''')
        self.executable('curl', '[[ "${FAIL_HEALTH:-0}" != 1 ]]')
        self.executable('sleep', ':')

    def executable(self, name, body):
        path = self.bin / name
        path.write_text('#!/usr/bin/env bash\n' + body + '\n')
        path.chmod(0o755)

    def run_deploy(self, **extra):
        env = dict(os.environ, PATH=f'{self.bin}:{os.environ["PATH"]}', DEPLOY_DIR=str(self.deploy),
                   CALLS=str(self.calls), OLD_IMAGE=OLD_IMAGE, **extra)
        return subprocess.run(['bash', str(ROOT / 'scripts/deploy.sh'), IMAGE, str(self.candidate)],
                              env=env, capture_output=True, text=True)

    def test_success_records_image_and_preserves_other_settings(self):
        result = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.env_file.read_text(), f"APP_IMAGE={IMAGE}\nDB_PASSWORD='keep$secret'\n")
        self.assertEqual((self.deploy / 'compose.prod.yaml').read_text(), 'candidate compose\n')

    def test_pull_failure_does_not_replace_running_container(self):
        result = self.run_deploy(FAIL_PULL='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(' up -d ', self.calls.read_text())
        self.assertIn(OLD_IMAGE, self.env_file.read_text())

    def test_failed_health_check_restores_previous_image(self):
        result = self.run_deploy(FAIL_HEALTH='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(f'{OLD_IMAGE} compose ', self.calls.read_text())
        self.assertIn(OLD_IMAGE, self.env_file.read_text())
        self.assertEqual((self.deploy / 'compose.prod.yaml').read_text(), 'previous compose\n')


if __name__ == '__main__':
    unittest.main()
