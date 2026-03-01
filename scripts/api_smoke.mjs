#!/usr/bin/env node
/**
 * API smoke test for Company Ops Demo.
 *
 * Usage:
 *   # Local (requires backend running):
 *   BASE_URL=http://localhost:8080 node scripts/api_smoke.mjs
 *
 *   # Deployed (Render):
 *   BASE_URL=https://company-ops-demo-api.onrender.com node scripts/api_smoke.mjs
 *
 * Notes:
 * - This is a smoke test (fast + practical), not exhaustive.
 * - It validates the core demo workflow rules and key endpoints.
 * - Deployed environments (e.g. Render free tier) may have cold starts; the first request can be slow.
 */

import assert from "node:assert/strict";

const BASE = process.env.BASE_URL || "http://localhost:8080";

async function http(path, { method = "GET", body, headers, token } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { "Authorization": `Bearer ${token}` } : {}),
      ...(headers || {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  return { ok: res.ok, status: res.status, data, text };
}

async function expectOk(resp, msg) {
  if (!resp.ok) {
    throw new Error(`${msg} (status=${resp.status}) body=${resp.text}`);
  }
}

async function resetDemo() {
  const r = await http("/api/demo/reset", { method: "POST" });
  await expectOk(r, "demo reset failed");
}

async function login(email) {
  const r = await http("/api/auth/login", { method: "POST", body: { email } });
  await expectOk(r, `login failed for ${email}`);
  assert.equal(typeof r.data?.id, "number", "login should return numeric id");
  assert.ok(r.data?.role, "login should return role");
  assert.ok(r.data?.token, "login should return token");
  return r.data;
}

async function searchAllAs(user) {
  const qs = new URLSearchParams({
    requesterId: String(user.id),
    requesterRole: user.role,
    q: "",
    sort: "activity_desc",
  });
  const r = await http(`/api/expense-reports/search?${qs.toString()}`, { token: user.token });
  await expectOk(r, "search failed");
  assert.ok(Array.isArray(r.data), "search should return array");
  return r.data;
}

function findByTitle(list, title) {
  const r = list.find((x) => x?.title === title);
  assert.ok(r, `expected seeded report not found: ${title}`);
  assert.equal(typeof r.id, "number", "report id must be number");
  return r;
}

async function getReport(id, token) {
  const r = await http(`/api/expense-reports/${id}`, { token });
  await expectOk(r, `get report ${id} failed`);
  return r.data;
}

async function getSpecialReview(id, token) {
  const r = await http(`/api/expense-reports/${id}/special-review`, { token });
  await expectOk(r, `get special review ${id} failed`);
  return r.data;
}

async function decideSpecialReview(id, payload, token) {
  const r = await http(`/api/expense-reports/${id}/special-review/decide`, {
    method: "POST",
    body: payload,
    token,
  });
  return r;
}

async function submitReport(id, payload, token) {
  const r = await http(`/api/expense-reports/${id}/submit`, { method: "POST", body: payload, token });
  return r;
}

async function createReport(payload, token) {
  const r = await http("/api/expense-reports", { method: "POST", body: payload, token });
  await expectOk(r, "create report failed");
  return r.data;
}

async function updateReport(id, payload, token) {
  const r = await http(`/api/expense-reports/${id}`, { method: "PUT", body: payload, token });
  return r;
}

async function approveReport(id, payload, token) {
  const r = await http(`/api/expense-reports/${id}/approve`, { method: "POST", body: payload, token });
  return r;
}

async function rejectReport(id, payload, token) {
  const r = await http(`/api/expense-reports/${id}/reject`, { method: "POST", body: payload, token });
  return r;
}

async function main() {
  console.log(`[api-smoke] BASE_URL=${BASE}`);

  // 1) Reset
  await resetDemo();
  console.log("[api-smoke] demo reset OK");

  // 2) Login
  const employee = await login("jun@example.com");
  const manager = await login("manager@example.com");
  const cfo = await login("finance@example.com");
  const ceo = await login("ceo@example.com");
  console.log("[api-smoke] login OK");

  // 3) Seed sanity via search (manager sees all)
  const all = await searchAllAs(manager);

  const draftClean = findByTitle(all, "Draft — Local Lunch");
  const financePending = findByTitle(all, "Draft — Hotel Exception (needs Finance)");
  const changesReq = findByTitle(all, "Changes requested — Meals cap exception");
  const submitted = findByTitle(all, "Submitted — NYC Trip");

  // 4) Submit clean DRAFT → MANAGER_REVIEW (normal approval chain entry)
  {
    const before = await getReport(draftClean.id, employee.token);
    assert.equal(before.status, "DRAFT");

    const s = await submitReport(draftClean.id, { submitterId: employee.id, reasons: [] }, employee.token);
    await expectOk(s, "submit clean draft failed");
    assert.equal(s.data, "MANAGER_REVIEW");

    const after = await getReport(draftClean.id, manager.token);
    assert.equal(after.status, "MANAGER_REVIEW");
  }
  console.log("[api-smoke] submit clean DRAFT -> MANAGER_REVIEW OK");

  // 5) CFO special review reject validations
  {
    const report = await getReport(financePending.id, cfo.token);
    assert.equal(report.status, "CFO_SPECIAL_REVIEW");

    const review = await getSpecialReview(financePending.id, cfo.token);
    assert.equal(review.status, "PENDING");
    assert.ok(review.items?.length >= 1, "special review should have items");

    // Reject first item WITHOUT financeReason => must fail (400)
    const first = review.items[0];

    const bad = await decideSpecialReview(financePending.id, {
      reviewerId: cfo.id,
      reviewerRole: "CFO",
      reviewerComment: "Rejecting due to policy.",
      decisions: review.items.map((it) => ({
        code: it.code,
        decision: it.code === first.code ? "REJECT" : "APPROVE",
        financeReason: it.code === first.code ? "" : "OK",
      })),
    }, cfo.token);

    assert.equal(bad.ok, false);
    assert.equal(bad.status, 400);

    // Reject first item WITH financeReason => must succeed and report becomes CHANGES_REQUESTED
    const good = await decideSpecialReview(financePending.id, {
      reviewerId: cfo.id,
      reviewerRole: "CFO",
      reviewerComment: "Please revise and resubmit.",
      decisions: review.items.map((it) => ({
        code: it.code,
        decision: it.code === first.code ? "REJECT" : "APPROVE",
        financeReason: it.code === first.code ? "Not eligible under policy." : "OK",
      })),
    }, cfo.token);

    await expectOk(good, "decide special review (reject) failed");
    assert.equal(good.data, "CHANGES_REQUESTED");

    const after = await getReport(financePending.id, cfo.token);
    assert.equal(after.status, "CHANGES_REQUESTED");
  }
  console.log("[api-smoke] CFO reject validation OK");

  // 6) Existing CHANGES_REQUESTED has feedback visible to submitter
  {
    const r = await http(
      `/api/expense-reports/${changesReq.id}/submitter-feedback?requesterId=${employee.id}`,
      { token: employee.token }
    );
    await expectOk(r, "submitter feedback fetch failed");
    assert.equal(r.data?.specialReviewStatus, "REJECTED");
    assert.ok(r.data?.reviewerComment, "feedback should include reviewerComment");
    assert.ok(
      r.data?.items?.some((x) => x.financeDecision === "REJECT" && x.financeReason),
      "feedback should include rejected item financeReason"
    );
  }
  console.log("[api-smoke] changes-requested feedback OK");

  // 7) Approval queue contains MANAGER_REVIEW report (manager view)
  {
    const q = await http("/api/expense-reports/pending-approval?requesterRole=MANAGER", { token: manager.token });
    await expectOk(q, "pending approval fetch failed");
    assert.ok(Array.isArray(q.data), "pending-approval should return array");
    assert.ok(
      q.data.some((x) => x.id === submitted.id && x.status === "MANAGER_REVIEW"),
      "expected seeded MANAGER_REVIEW report in manager approval queue"
    );
  }
  console.log("[api-smoke] approval queue OK");

  // 8) Exception review approve path routes back into normal queue
  {
    // reset again for a clean approve scenario
    await resetDemo();

    const employee2 = await login("jun@example.com");
    const manager2 = await login("manager@example.com");
    const cfo2 = await login("finance@example.com");

    const all2 = await searchAllAs(manager2);
    const exceptionReport = findByTitle(all2, "Draft — Hotel Exception (needs Finance)");

    const r0 = await getReport(exceptionReport.id, cfo2.token);
    assert.equal(r0.status, "CFO_SPECIAL_REVIEW");

    const review = await getSpecialReview(exceptionReport.id, cfo2.token);
    assert.equal(review.status, "PENDING");
    assert.ok(review.items?.length >= 1, "special review should have items");

    const ok = await decideSpecialReview(exceptionReport.id, {
      reviewerId: cfo2.id,
      reviewerRole: "CFO",
      reviewerComment: "Approved exceptions for demo.",
      decisions: review.items.map((it) => ({
        code: it.code,
        decision: "APPROVE",
        financeReason: "OK",
      })),
    }, cfo2.token);
    await expectOk(ok, "decide special review (approve) failed");
    assert.equal(ok.data, "MANAGER_REVIEW");

    const r1 = await getReport(exceptionReport.id, manager2.token);
    assert.equal(r1.status, "MANAGER_REVIEW");

    // special review should be deleted on approve
    const srAfter = await http(
      `/api/expense-reports/${exceptionReport.id}/special-review`,
      { token: cfo2.token }
    );
    assert.equal(srAfter.ok, false);
    assert.equal(srAfter.status, 400);

    // manager queue should contain the report now
    const q2 = await http("/api/expense-reports/pending-approval?requesterRole=MANAGER", { token: manager2.token });
    await expectOk(q2, "pending approval fetch failed");
    assert.ok(q2.data.some((x) => x.id === exceptionReport.id && x.status === "MANAGER_REVIEW"));

    assert.ok(employee2.id);
  }
  console.log("[api-smoke] exception review approve path OK");

  // 9) Full loop: exception reject -> changes requested -> fix -> resubmit -> approve chain
  {
    await resetDemo();

    const employee2 = await login("jun@example.com");
    const manager2 = await login("manager@example.com");
    const cfo2 = await login("finance@example.com");

    // Create an exception report (Hotel cap is 250; set 400 to trigger warning)
    const id = await createReport({
      submitterId: employee2.id,
      title: "API Smoke — Hotel Exception Loop",
      items: [
        { date: "2026-01-10", description: "Hotel night", amount: 400, category: "Hotel" },
      ],
    }, employee2.token);

    // Set destination and dates via update (CreateRequest doesn't have these fields)
    await updateReport(id, {
      submitterId: employee2.id,
      title: "API Smoke — Hotel Exception Loop",
      destination: "Boston, United States",
      departureDate: "2026-01-10",
      returnDate: "2026-01-10",
      items: [
        { date: "2026-01-10", description: "Hotel night", amount: 400, category: "Hotel" },
      ],
    }, employee2.token);

    const s1 = await submitReport(id, { submitterId: employee2.id, reasons: [] }, employee2.token);
    await expectOk(s1, "submit exception report failed");
    assert.equal(s1.data, "CFO_SPECIAL_REVIEW");

    const review = await getSpecialReview(id, cfo2.token);
    assert.ok(review.items?.length >= 1);

    // CFO rejects (requires per-item reason + overall comment)
    const bad = await decideSpecialReview(id, {
      reviewerId: cfo2.id,
      reviewerRole: "CFO",
      reviewerComment: "Please revise and resubmit.",
      decisions: review.items.map((it) => ({ code: it.code, decision: "REJECT", financeReason: "" })),
    }, cfo2.token);
    assert.equal(bad.ok, false);
    assert.equal(bad.status, 400);

    const good = await decideSpecialReview(id, {
      reviewerId: cfo2.id,
      reviewerRole: "CFO",
      reviewerComment: "Please reduce the amount or provide justification.",
      decisions: review.items.map((it) => ({ code: it.code, decision: "REJECT", financeReason: "Over cap." })),
    }, cfo2.token);
    await expectOk(good, "decide special review reject failed");
    assert.equal(good.data, "CHANGES_REQUESTED");

    // Submitter can fetch feedback
    const fb = await http(
      `/api/expense-reports/${id}/submitter-feedback?requesterId=${employee2.id}`,
      { token: employee2.token }
    );
    await expectOk(fb, "submitter feedback fetch failed");
    assert.equal(fb.data?.specialReviewStatus, "REJECTED");

    // Fix the report: reduce hotel amount to pass policy
    const up = await updateReport(id, {
      submitterId: employee2.id,
      title: "API Smoke — Hotel Fixed",
      destination: "Boston, United States",
      departureDate: "2026-01-10",
      returnDate: "2026-01-10",
      items: [
        { date: "2026-01-10", description: "Hotel night (fixed)", amount: 240, category: "Hotel" },
      ],
    }, employee2.token);
    await expectOk(up, "update changes requested failed");
    assert.equal(up.data, "CHANGES_REQUESTED");

    const s2 = await submitReport(id, { submitterId: employee2.id, reasons: [] }, employee2.token);
    await expectOk(s2, "resubmit fixed report failed");
    assert.equal(s2.data, "MANAGER_REVIEW");

    // Normal approval chain (employee submitter): manager -> CFO -> APPROVED
    const a1 = await approveReport(id, { approverId: manager2.id, approverRole: "MANAGER", comment: "OK" }, manager2.token);
    await expectOk(a1, "manager approve failed");
    assert.equal((await getReport(id, manager2.token)).status, "CFO_REVIEW");

    const a2 = await approveReport(id, { approverId: cfo2.id, approverRole: "CFO", comment: "OK" }, cfo2.token);
    await expectOk(a2, "cfo approve failed");
    assert.equal((await getReport(id, cfo2.token)).status, "APPROVED");
  }
  console.log("[api-smoke] exception loop + approve chain OK");

  console.log("[api-smoke] ✅ ALL OK");
}

main().catch((e) => {
  console.error("[api-smoke] ❌ FAILED");
  console.error(e);
  process.exit(1);
});
