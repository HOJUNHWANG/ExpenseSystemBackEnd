# Company Ops Demo — End-to-End Test Cases

This doc is a practical test plan for the **public demo workflow** (1-person, multi-role switch) and the core APIs.

> Tip: Always start by resetting demo data so IDs/statuses are predictable.

---

## 0) Prereqs / Setup

### Accounts (seeded by `/api/demo/reset`)
- Employee: `jun@example.com`
- Manager: `manager@example.com`
- CFO: `finance@example.com`
- CEO: `ceo@example.com`

### Key URLs
- Frontend: (your Vercel URL)
- Backend: `https://company-ops-demo-api.onrender.com`

### Reset
- UI: click **Reset demo**
- API: `POST /api/demo/reset`

---

## 1) Seeded default data (after reset)

After reset, seeded reports cover major states:

1) **DRAFT** (policy-clean)
   - Title: `Draft — Local Lunch`
   - Expect: submit → `MANAGER_REVIEW`

2) **CFO_SPECIAL_REVIEW** (exception review pending)
   - Title: `Draft — Hotel Exception (needs Finance)` *(seed title kept)*
   - Has exception review items (examples):
     - `HOTEL_ABOVE_CAP`
     - `MEALS_ABOVE_DAILY_CAP`
     - `ENTERTAINMENT_ABOVE_CAP`
     - `AIRFARE_ABOVE_CAP`
     - `ITEM_DATE_OUTSIDE_TRIP`

3) **CHANGES_REQUESTED** (exception review rejected)
   - Title: `Changes requested — Meals cap exception`
   - Feedback visible to submitter via:
     - `GET /api/expense-reports/{id}/submitter-feedback?requesterId=...`

4) **MANAGER_REVIEW** (pending manager approval)
   - Title: `Submitted — NYC Trip` *(seed title kept)*

5) **APPROVED**
   - Title: `Approved — Client Visit`

6) **REJECTED**
   - Title: `Rejected — Missing details`

---

## 2) UI Smoke Tests (happy paths)

### A. Guided Demo boot
1. Open `/dashboard`
2. Run **Guided Demo**
3. Expect:
   - demo reset occurs
   - logged in as Employee (Role Switcher shows Employee)
   - app renders without errors

### B. Employee: Submit a clean DRAFT (no exceptions)
1. Role: **Employee**
2. Go to **My Reports**
3. Open report `Draft — Local Lunch`
4. Click **Submit**
5. Expect:
   - status changes to `MANAGER_REVIEW`
   - report appears in **Approval Queue** when viewing as Manager

### C. Manager: Approve a `MANAGER_REVIEW` report
1. Role: **Manager**
2. Go to **Approval Queue**
3. Open `Submitted — NYC Trip`
4. Approve with a comment
5. Expect:
   - status becomes `CFO_REVIEW`

### D. CFO: Final approve (employee submitter)
1. Role: **CFO**
2. Go to **Approval Queue**
3. Approve the report you just moved to `CFO_REVIEW`
4. Expect:
   - status becomes `APPROVED`

### E. Employee: CFO changes requested → edit → resubmit loop
1. Role: **Employee**
2. Open `Changes requested — Meals cap exception`
3. Expect: a **CFO requested changes** panel is visible
4. Click **Edit**, adjust items so warnings resolve (e.g. meals total <= $75/day)
5. Save changes
6. Click **Submit**
7. Expect:
   - if no warnings remain → routes into the normal approval chain (`MANAGER_REVIEW`)
   - if warnings remain → report routes to exception review (`CFO_SPECIAL_REVIEW` or `CEO_SPECIAL_REVIEW`)

### F. CFO: Exception review decision validation
1. Role: **CFO**
2. Open a report in `CFO_SPECIAL_REVIEW`
3. Choose `Reject` for one warning item
4. Leave that item’s finance note empty
5. Attempt to submit decision
6. Expect:
   - UI blocks submit and shows inline validation
   - Backend would reject with 400 if bypassed

---

## 3) Role-based Search Tests

### A. Employee scope
1. Role: Employee
2. Go to `/search`
3. Expect: only own reports are returned.

### B. Manager/CFO/CEO scope
1. Role: Manager or CFO or CEO
2. Go to `/search`
3. Expect: can see all reports (within demo DB), and status filter/sort works.

---

## 4) API Test Cases (curl-style)

> You can run these against local or deployed backend. Replace `${BASE}` accordingly.

### A. Reset
```bash
BASE=https://company-ops-demo-api.onrender.com
curl -sS -X POST "$BASE/api/demo/reset"
```
Expect: `200 OK`

### B. Login
```bash
curl -sS -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"jun@example.com"}'
```
Expect: user object with `id`, `role`.

### C. Pending approval list
```bash
curl -sS "$BASE/api/expense-reports/pending-approval?requesterRole=MANAGER"
```
Expect: list includes items in `MANAGER_REVIEW`.

### D. Get exception review (API path kept as /special-review)
```bash
curl -sS "$BASE/api/expense-reports/{id}/special-review"
```
Expect: `items[]` with warning `code`s.

### E. Decide exception review — negative validation
API path: `/special-review/decide`
- If any decision is `REJECT`, that item must include `financeReason`.
- If any reject exists, request must include non-empty `reviewerComment`.

---

## 5) Regression Checklist (quick)
- [ ] `/api/demo/reset` works on Postgres (no FK violations)
- [ ] DRAFT create/save works
- [ ] Submit DRAFT without warnings → routes into normal chain (`MANAGER_REVIEW` for employee)
- [ ] Submit with warnings → exception review (`CFO_SPECIAL_REVIEW` / `CEO_SPECIAL_REVIEW`)
- [ ] Reject in exception review requires per-item `financeReason` + global `reviewerComment`
- [ ] Any reject → `CHANGES_REQUESTED` + feedback visible to submitter
- [ ] All approve → exception review deleted + report routes into normal approval chain
- [ ] Approval queue is role-based via `requesterRole`
- [ ] Mobile: header menu works and tables don’t overflow
