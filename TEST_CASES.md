# Company Ops Demo — End-to-End Test Cases

This doc is a practical test plan for the **public demo workflow** (1-person, 3-role switch) and the core APIs.

> Tip: Always start by resetting demo data so IDs/statuses are predictable.

---

## 0) Prereqs / Setup

### Accounts (seeded by `/api/demo/reset`)
- Employee: `jun@example.com`
- Manager: `manager@example.com`
- Finance: `finance@example.com`

### Key URLs
- Frontend: (your Vercel URL)
- Backend: `https://company-ops-demo-api.onrender.com`

### Reset
- UI: click **Reset demo**
- API: `POST /api/demo/reset`

---

## 1) Seeded default data (what should exist after reset)

After reset, seeded reports cover all major states:

1) **DRAFT** (no warnings)
   - Title: `Draft — Local Lunch`
   - Purpose: submit directly → SUBMITTED

2) **FINANCE_SPECIAL_REVIEW** (pending)
   - Title: `Draft — Hotel Exception (needs Finance)`
   - Has `SpecialReview` in `PENDING` with items for warning codes:
     - `HOTEL_ABOVE_CAP`
     - `RECEIPT_REQUIRED`

3) **CHANGES_REQUESTED** (finance already rejected at least one item)
   - Title: `Changes requested — Meals cap exception`
   - Has `SpecialReview` in `REJECTED` with at least one item:
     - code `MEALS_ABOVE_DAILY_CAP`
     - has **financeDecision=REJECT** and **financeReason present**
     - has global **reviewerComment present**

4) **SUBMITTED** (pending manager approval)
   - Title: `Submitted — NYC Trip`

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
   - you land on reports/dashboard without errors

### B. Employee: Submit a clean DRAFT (no exceptions)
1. Role: **Employee**
2. Go to **My Reports**
3. Open report `Draft — Local Lunch`
4. Click **Submit**
5. Expect:
   - status changes to `SUBMITTED`
   - report appears in **Approval Queue** when viewing as Manager

### C. Manager: Approve a SUBMITTED report
1. Role: **Manager**
2. Go to **Approval Queue**
3. Open `Submitted — NYC Trip`
4. Approve with a comment
5. Expect:
   - status becomes `APPROVED`
   - it appears in Recent activity as Approved

### D. Employee: Finance changes requested → edit → resubmit loop
1. Role: **Employee**
2. Open `Changes requested — Meals cap exception`
3. Expect: a **Finance requested changes** panel is visible
4. Click **Edit**, adjust items so meals total <= $75/day (or otherwise resolve)
5. Save changes
6. Click **Submit**
7. Expect:
   - if no warnings remain → `SUBMITTED`
   - if warnings remain → modal requires per-warning reasons

### E. Finance: Special approval decision validation (must provide per-item reason on reject)
1. Role: **Finance**
2. Open a report in `FINANCE_SPECIAL_REVIEW`
3. Choose `✕ Reject` for one warning item
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
3. Search a keyword unique to another user (if added later) or verify results are only submitter-owned.
4. Expect: only own reports are returned.

### B. Manager/Finance scope
1. Role: Manager or Finance
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
curl -sS "$BASE/api/expense-reports/pending-approval"
```
Expect: list includes at least 1 `SUBMITTED`.

### D. Get special review
```bash
curl -sS "$BASE/api/expense-reports/{id}/special-review"
```
Expect: `items[]` with warning `code`s.

### E. Decide special review — negative validation
- If any decision is `REJECT`, that item must include `financeReason`.
- If any reject exists, request must include non-empty `reviewerComment`.

---

## 5) Regression Checklist (quick)
- [ ] `/api/demo/reset` works on Postgres (no FK violations)
- [ ] DRAFT create/save works
- [ ] Submit DRAFT without warnings → SUBMITTED
- [ ] Submit with warnings requires reasons → FINANCE_SPECIAL_REVIEW
- [ ] Finance reject requires per-item financeReason + global reviewerComment
- [ ] Any finance reject → CHANGES_REQUESTED + feedback visible to submitter
- [ ] Finance all approve → special review deleted + report → SUBMITTED
- [ ] Approval queue only shows SUBMITTED
- [ ] Mobile: header menu works and tables don’t overflow
