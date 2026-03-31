START TRANSACTION;

-- optional cleanup before rerun
DELETE FROM cases WHERE patient_code >= 8000;

INSERT INTO cases (
    patient_code,
    province_code,
    province_name,
    consent,
    patient_metadata,
    image_path,
    technician_id,
    location,
    status,
    analysis_status,
    created_at,
    uploaded_by
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10
),
doctor_geo AS (
    SELECT 'du01' AS username, '63' AS p_code, 'Tak' AS p_name, '71' AS s_code, 'Kanchanaburi' AS s_name
    UNION ALL SELECT 'du02', '58', 'Mae Hong Son', '57', 'Chiang Rai'
    UNION ALL SELECT 'du03', '57', 'Chiang Rai', '50', 'Chiang Mai'
    UNION ALL SELECT 'du04', '71', 'Kanchanaburi', '63', 'Tak'
    UNION ALL SELECT 'du05', '85', 'Ranong', '86', 'Chumphon'
    UNION ALL SELECT 'du06', '27', 'Sa Kaeo', '23', 'Trat'
    UNION ALL SELECT 'du07', '23', 'Trat', '27', 'Sa Kaeo'
    UNION ALL SELECT 'du08', '96', 'Narathiwat', '95', 'Yala'
    UNION ALL SELECT 'du09', '95', 'Yala', '94', 'Pattani'
    UNION ALL SELECT 'du10', '94', 'Pattani', '96', 'Narathiwat'
),
gen AS (
    SELECT
        d.username,
        seq.n AS case_no,
        -- Use numeric patient_code: 8000 + (user_index * 10) + case_no
        (8000 + (CAST(SUBSTRING(d.username, 3) AS UNSIGNED) * 10) + seq.n) AS patient_code,
        CASE WHEN seq.n <= 8 THEN d.p_code ELSE d.s_code END AS province_code,
        CASE WHEN seq.n <= 8 THEN d.p_name ELSE d.s_name END AS province_name,
        CASE WHEN seq.n <= 8 THEN d.p_name ELSE d.s_name END AS location_name,
        NOW() - INTERVAL ((seq.n - 1) + (ASCII(RIGHT(d.username, 1)) - 48) * 2) DAY AS created_at
    FROM doctor_geo d
    CROSS JOIN seq
)
SELECT
    g.patient_code,
    g.province_code,
    g.province_name,
    FALSE,
    NULL,
    CONCAT('mock/images/', g.patient_code, '.jpg') as image_path,
    CONCAT('TECH-', UPPER(g.username)),
    g.location_name,
    'ANALYZED',
    'COMPLETED',
    g.created_at,
    u.id
FROM gen g
JOIN users u ON u.username = g.username
WHERE u.role = 'DATA_USE';

COMMIT;
