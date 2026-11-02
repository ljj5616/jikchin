-- 2026 KBO and K League 1 clubs. Keep any reference data already present.
ALTER TABLE team ADD COLUMN onboarding_selectable BIT NOT NULL DEFAULT b'0';
INSERT INTO league (code) SELECT 'KBO' WHERE NOT EXISTS (SELECT 1 FROM league WHERE code = 'KBO');
INSERT INTO league (code) SELECT 'KLEAGUE' WHERE NOT EXISTS (SELECT 1 FROM league WHERE code = 'KLEAGUE');

INSERT INTO team (name, short_name, logo_url, league_id)
SELECT seed.name, seed.short_name, '', league.id
FROM league
JOIN (
    SELECT 'KBO' AS league_code, 'KIA 타이거즈' AS name, 'KIA' AS short_name
    UNION ALL SELECT 'KBO', '삼성 라이온즈', '삼성'
    UNION ALL SELECT 'KBO', 'LG 트윈스', 'LG'
    UNION ALL SELECT 'KBO', '두산 베어스', '두산'
    UNION ALL SELECT 'KBO', 'KT 위즈', 'KT'
    UNION ALL SELECT 'KBO', 'SSG 랜더스', 'SSG'
    UNION ALL SELECT 'KBO', '롯데 자이언츠', '롯데'
    UNION ALL SELECT 'KBO', '한화 이글스', '한화'
    UNION ALL SELECT 'KBO', 'NC 다이노스', 'NC'
    UNION ALL SELECT 'KBO', '키움 히어로즈', '키움'
    UNION ALL SELECT 'KLEAGUE', '강원 FC', '강원'
    UNION ALL SELECT 'KLEAGUE', '광주 FC', '광주'
    UNION ALL SELECT 'KLEAGUE', '김천상무', '김천'
    UNION ALL SELECT 'KLEAGUE', '대전하나시티즌', '대전'
    UNION ALL SELECT 'KLEAGUE', '부천 FC', '부천'
    UNION ALL SELECT 'KLEAGUE', 'FC 서울', '서울'
    UNION ALL SELECT 'KLEAGUE', 'FC 안양', '안양'
    UNION ALL SELECT 'KLEAGUE', '울산 HD', '울산'
    UNION ALL SELECT 'KLEAGUE', '인천유나이티드', '인천'
    UNION ALL SELECT 'KLEAGUE', '전북현대', '전북'
    UNION ALL SELECT 'KLEAGUE', '제주 SK', '제주'
    UNION ALL SELECT 'KLEAGUE', '포항스틸러스', '포항'
) AS seed ON seed.league_code = league.code
WHERE NOT EXISTS (
    SELECT 1 FROM team existing WHERE existing.league_id = league.id AND existing.name = seed.name
);

UPDATE team t JOIN league l ON t.league_id = l.id
SET t.onboarding_selectable = b'1'
WHERE (l.code = 'KBO' AND t.name IN (
    'KIA 타이거즈', '삼성 라이온즈', 'LG 트윈스', '두산 베어스', 'KT 위즈',
    'SSG 랜더스', '롯데 자이언츠', '한화 이글스', 'NC 다이노스', '키움 히어로즈'
)) OR (l.code = 'KLEAGUE' AND t.name IN (
    '강원 FC', '광주 FC', '김천상무', '대전하나시티즌', '부천 FC', 'FC 서울',
    'FC 안양', '울산 HD', '인천유나이티드', '전북현대', '제주 SK', '포항스틸러스'
));
