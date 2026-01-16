# Business Layer APIs - Program Management

## Tổng quan
3 API chuyên nghiệp để quản lý Chương trình, Chu kỳ và tự động hóa liên kết với Meta Process.

---

## API 1: Tạo Chương trình (uce_prog)

**Endpoint:** `POST /api/v2/programs`

**Description:** Tạo mới một chương trình với đầy đủ thuộc tính

**Request Body:**
```json
{
  "progName": "QUAN_LY_DIEM_SINH_VIEN",
  "companyId": 1,
  "brandId": 10,
  "departmentId": 100,
  "progNote": "Hệ thống quản lý điểm và xét đậu/rớt sinh viên",
  "status": "DECLARED",
  "progType": "NOTARGET",
  "progSpec": {
    "sourceDatabase": "test_sinh_vien",
    "sourceSchema": "test_sv",
    "targetSchema": "pipeline_sinh_vien",
    "calcSchema": "sinh_vien_calc",
    "academicYear": "2025-2026",
    "owner": "Phòng Đào tạo"
  }
}
```

**Response:**
```json
{
  "progId": 1,
  "progName": "QUAN_LY_DIEM_SINH_VIEN",
  "companyId": 1,
  "brandId": 10,
  "departmentId": 100,
  "progNote": "Hệ thống quản lý điểm và xét đậu/rớt sinh viên",
  "status": "DECLARED",
  "progType": "NOTARGET",
  "progSpec": {
    "sourceDatabase": "test_sinh_vien",
    "sourceSchema": "test_sv",
    "targetSchema": "pipeline_sinh_vien",
    "calcSchema": "sinh_vien_calc",
    "academicYear": "2025-2026",
    "owner": "Phòng Đào tạo"
  }
}
```

**Validation:**
- `progName` phải unique
- `status` mặc định: "DECLARED"
- `progType` mặc định: "NOTARGET"

---

## API 2: Tạo Chu kỳ (uce_prog_period)

**Endpoint:** `POST /api/v2/programs/{progId}/periods`

**Description:** Tạo mới chu kỳ cho một chương trình cụ thể

**Request Body:**
```json
{
  "periodName": "HK1_2026",
  "periodSpec": {
    "hocKy": "HK1",
    "nam": 2026,
    "namHoc": 2,
    "academicYear": "2025-2026",
    "startMonth": "09-2025",
    "endMonth": "01-2026"
  },
  "execMode": {
    "mode": "MANUAL",
    "schedule": "0 2 1 * *",
    "retryPolicy": {
      "maxRetries": 3,
      "retryDelay": 300
    }
  },
  "startDate": "2025-09-01T00:00:00",
  "endDate": "2026-01-31T23:59:59",
  "status": "ACTIVE"
}
```

**Response:**
```json
{
  "periodId": 1,
  "periodName": "HK1_2026",
  "progId": 1,
  "periodSpec": {
    "hocKy": "HK1",
    "nam": 2026,
    "namHoc": 2,
    "academicYear": "2025-2026",
    "startMonth": "09-2025",
    "endMonth": "01-2026"
  },
  "execMode": {
    "mode": "MANUAL",
    "schedule": "0 2 1 * *",
    "retryPolicy": {
      "maxRetries": 3,
      "retryDelay": 300
    }
  },
  "startDate": "2025-09-01T00:00:00",
  "endDate": "2026-01-31T23:59:59",
  "status": "ACTIVE"
}
```

**Validation:**
- `progId` phải tồn tại
- `periodName` phải unique
- `status` mặc định: "ACTIVE"

---

## API 3: Liên kết + Tạo Process + Tạo DAG (Tự động)

**Endpoint:** `POST /api/v2/programs/link`

**Description:** Liên kết Program/Period với Meta Process, tự động tạo Process Instance và sync DAG

**Request Body:**
```json
{
  "progId": 1,
  "periodId": 1,
  "metaProcCode": "SINH_VIEN_CALC_2026",
  "useVar": {
    "p_hoc_ky": "HK1",
    "p_nam": 2026,
    "p_nam_hoc": 2,
    "source_schema": "pipeline_sinh_vien",
    "calc_schema": "sinh_vien_calc"
  },
  "connectionName": "airflow_local",
  "dagDirectory": "D:/airflow/dags",
  "isActive": true
}
```

**Response:**
```json
{
  "progUseMpId": 1,
  "progId": 1,
  "periodId": 1,
  "metaProcCode": "SINH_VIEN_CALC_2026",
  "processId": 123,
  "processCode": "SINH_VIEN_CALC_2026_P1_PER1_001",
  "dagId": "SINH_VIEN_CALC_2026_P1_PER1_001",
  "connectionName": "airflow_local",
  "message": "Successfully linked and created Process 'SINH_VIEN_CALC_2026_P1_PER1_001' with DAG 'SINH_VIEN_CALC_2026_P1_PER1_001'"
}
```

**Chức năng tự động:**
1. ✅ Validate Program và Period tồn tại
2. ✅ Check duplicate link
3. ✅ **Tự động tạo Process Instance** (gọi logic `/api/v2/process/create`)
4. ✅ **Tự động sync DAG** (gọi logic `/api/v2/airflow/sync-process-to-dag`)
5. ✅ Tạo bản ghi liên kết trong `uce_prog_use_meta_process`
6. ✅ Trả về đầy đủ thông tin Process và DAG đã tạo

**Validation:**
- `progId`, `periodId`, `metaProcCode` phải tồn tại
- Không được duplicate link (cùng progId + periodId + metaProcCode)
- `isActive` mặc định: true

---

## Workflow hoàn chỉnh

### Bước 1: Tạo Meta Process (existing API)
```bash
POST /api/v2/meta-process
```

### Bước 2: Tạo Chương trình
```bash
POST /api/v2/programs
{
  "progName": "QUAN_LY_DIEM_SINH_VIEN",
  "companyId": 1,
  "progNote": "Hệ thống quản lý điểm sinh viên",
  "status": "DECLARED"
}
```

### Bước 3: Tạo Chu kỳ
```bash
POST /api/v2/programs/1/periods
{
  "periodName": "HK1_2026",
  "periodSpec": {"hocKy": "HK1", "nam": 2026, "namHoc": 2},
  "startDate": "2025-09-01T00:00:00",
  "endDate": "2026-01-31T23:59:59"
}
```

### Bước 4: Liên kết (tự động tạo Process + DAG)
```bash
POST /api/v2/programs/link
{
  "progId": 1,
  "periodId": 1,
  "metaProcCode": "SINH_VIEN_CALC_2026",
  "useVar": {"p_hoc_ky": "HK1", "p_nam": 2026, "p_nam_hoc": 2},
  "connectionName": "airflow_local",
  "dagDirectory": "D:/airflow/dags"
}
```

### Bước 5: Trigger DAG (existing API)
```bash
POST /api/v2/airflow/trigger/{dagId}
```

---

## Additional APIs

### Lấy danh sách chương trình theo công ty
```bash
GET /api/v2/programs/by-company/{companyId}
```

### Lấy danh sách chu kỳ của một chương trình
```bash
GET /api/v2/programs/{progId}/periods
```

### Lấy danh sách liên kết theo chương trình
```bash
GET /api/v2/programs/link/by-program/{progId}
```

### Lấy danh sách liên kết theo chu kỳ
```bash
GET /api/v2/programs/link/by-period/{periodId}
```

---

## Database Schema

### uce_program.uce_prog
- prog_id (PK)
- prog_name (UNIQUE)
- company_id, brand_id, department_id
- prog_spec (JSONB)
- status, prog_type

### uce_program.uce_prog_period
- period_id (PK)
- period_name (UNIQUE)
- prog_id (FK)
- period_spec, exec_mode (JSONB)
- start_date, end_date, status

### uce_program.uce_prog_use_meta_process
- prog_use_mp_id (PK)
- prog_id (FK)
- period_id (FK)
- meta_proc_code
- use_var (JSONB)
- dag_id, connection_id
- is_active

---

## Error Handling

**400 Bad Request:**
- Thiếu required fields
- Invalid JSON format

**404 Not Found:**
- Program không tồn tại
- Period không tồn tại
- Meta Process không tồn tại

**409 Conflict:**
- Duplicate progName
- Duplicate periodName
- Duplicate link

**500 Internal Server Error:**
- Lỗi tạo Process
- Lỗi sync DAG
- Database connection error
