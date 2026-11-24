#!/usr/bin/env python3
"""Send the checked-out deployment files to EC2 and wait for SSM's result."""
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time


def aws(*args, check=True):
    return subprocess.run(["aws", *args, "--output", "json"], text=True,
                          capture_output=True, check=check)


def main():
    instance = os.environ.get("EC2_INSTANCE_ID", "")
    image = os.environ.get("IMAGE_URI", "")
    if not re.fullmatch(r"i-(?:[0-9a-f]{8}|[0-9a-f]{17})", instance):
        raise ValueError("Set the EC2_INSTANCE_ID repository variable to the target instance ID.")
    if not re.fullmatch(r"[0-9]{12}\.dkr\.ecr\.ap-northeast-2\.amazonaws\.com/jikchin-app:[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}", image):
        raise ValueError("Invalid production ECR image URI.")

    root = Path(__file__).resolve().parent.parent
    script = base64.b64encode((root / "scripts/deploy.sh").read_bytes()).decode("ascii")
    compose = base64.b64encode((root / "compose.prod.yaml").read_bytes()).decode("ascii")
    command = (
        "set -eu; workdir=$(mktemp -d); trap 'rm -rf \"$workdir\"' EXIT; "
        f"printf %s '{script}' | base64 -d > \"$workdir/deploy.sh\"; "
        f"printf %s '{compose}' | base64 -d > \"$workdir/compose.prod.yaml\"; "
        f"bash \"$workdir/deploy.sh\" '{image}' \"$workdir/compose.prod.yaml\""
    )
    params = json.dumps({"commands": [command], "executionTimeout": ["600"]})
    result = aws("ssm", "send-command", "--instance-ids", instance,
                 "--document-name", "AWS-RunShellScript", "--timeout-seconds", "120",
                 "--parameters", params)
    command_id = json.loads(result.stdout)["Command"]["CommandId"]
    print(f"SSM command {command_id} sent to {instance}.", flush=True)
    deadline = time.monotonic() + 720
    while time.monotonic() < deadline:
        result = aws("ssm", "get-command-invocation", "--command-id", command_id,
                     "--instance-id", instance, check=False)
        if result.returncode:
            if "InvocationDoesNotExist" not in result.stderr:
                raise RuntimeError(result.stderr.strip())
        else:
            invocation = json.loads(result.stdout)
            status = invocation["Status"]
            if status not in ("Pending", "InProgress", "Delayed", "Cancelling"):
                print(invocation.get("StandardOutputContent", ""), end="")
                print(invocation.get("StandardErrorContent", ""), end="", file=sys.stderr)
                if status != "Success":
                    raise RuntimeError(f"EC2 deployment failed: {status}")
                print("EC2 deployment succeeded.")
                return
        time.sleep(5)
    raise TimeoutError("Timed out waiting for the EC2 deployment; inspect the SSM command before retrying.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, TimeoutError, subprocess.CalledProcessError) as exc:
        print(f"Deployment error: {exc}", file=sys.stderr)
        if isinstance(exc, subprocess.CalledProcessError):
            print(exc.stderr, file=sys.stderr)
        sys.exit(1)
