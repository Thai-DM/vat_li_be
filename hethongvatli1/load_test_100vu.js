import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// Custom metric trends for endpoint breakdown
export const t_student_browse = new Trend('t_student_browse', true);
export const t_student_materials = new Trend('t_student_materials', true);
export const t_student_weak_topics = new Trend('t_student_weak_topics', true);
export const t_student_practice_rec = new Trend('t_student_practice_rec', true);
export const t_student_practice_submit = new Trend('t_student_practice_submit', true);
export const t_instructor_classes = new Trend('t_instructor_classes', true);
export const t_instructor_weak_summary = new Trend('t_instructor_weak_summary', true);
export const t_admin_queries = new Trend('t_admin_queries', true);
export const t_chunk_upload_100k = new Trend('t_chunk_upload_100k', true);
export const t_chunk_merge = new Trend('t_chunk_merge', true);

export const errorRate = new Rate('custom_error_rate');
export const successCount = new Counter('custom_success_count');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CLASS_ID = '1798491a-64b0-4b63-bd5a-7f80898125d5'; // PHY101-01

// Setup execution options
export const options = {
  stages: [
    { duration: '20s', target: 20 },   // Warm-up
    { duration: '30s', target: 100 },  // Ramp-up to 100 VUs
    { duration: '2m', target: 100 },   // Steady-state at 100 VUs
    { duration: '20s', target: 0 },    // Ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<=3000'], // SLA P95 <= 3000ms
    http_req_failed: ['rate<0.01'],     // Error rate < 1%
  },
};

// Global setup to retrieve tokens once before load test
export function setup() {
  const users = [
    { username: 'admin', password: 'admin123456', role: 'ADMIN' },
    { username: 'gv_nguyen', password: 'gv_nguyen123456', role: 'INSTRUCTOR' },
    { username: 'gv_tran', password: 'gv_tran123456', role: 'INSTRUCTOR' },
    { username: 'sv_an', password: 'sv_an123456', role: 'STUDENT' },
    { username: 'sv_binh', password: 'sv_binh123456', role: 'STUDENT' },
    { username: 'sv_cuong', password: 'sv_cuong123456', role: 'STUDENT' },
    { username: 'sv_dung', password: 'sv_dung123456', role: 'STUDENT' },
  ];

  const tokens = {};
  for (const u of users) {
    const res = http.post(
      `${BASE_URL}/api/v1/users/signin`,
      JSON.stringify({ username: u.username, password: u.password }),
      { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
    );
    if (res.status === 200) {
      tokens[u.username] = res.json('data.accessToken');
    } else {
      console.error(`Failed to login ${u.username}: status ${res.status}`);
    }
  }

  // Pre-generate 100KB binary chunk content
  const chunkBytes = 'X'.repeat(102400);

  return { tokens, chunkBytes };
}

function getRandomUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export default function (data) {
  const tokens = data.tokens;
  const chunkBytes = data.chunkBytes;

  // Distribute VUs across realistic activities
  // VU distribution:
  // - 0 to 34: Student Browse & Agenda (35%)
  // - 35 to 54: Student Practice Recommendation & Quiz Submission (20%)
  // - 55 to 69: Student Weak Topics Query (15%)
  // - 70 to 84: Instructor Dashboard & Weak Topics Summary (15%)
  // - 85 to 94: Chunked File Upload & Merge (10%)
  // - 95 to 99: Admin & System Operations (5%)
  const vuId = __VU % 100;

  const studentUsers = ['sv_an', 'sv_binh', 'sv_cuong', 'sv_dung'];
  const studentUser = studentUsers[__VU % studentUsers.length];
  const studentToken = tokens[studentUser] || tokens['sv_an'];
  const instToken = tokens['gv_nguyen'];
  const adminToken = tokens['admin'];

  const studentHeaders = {
    Authorization: `Bearer ${studentToken}`,
  };
  const instHeaders = {
    Authorization: `Bearer ${instToken}`,
  };
  const adminHeaders = {
    Authorization: `Bearer ${adminToken}`,
  };

  if (vuId < 35) {
    // ----------------------------------------------------
    // ACTIVITY GROUP 1: Student Browse & Study Material
    // ----------------------------------------------------
    const resClasses = http.get(`${BASE_URL}/api/v1/students/me/classes`, { headers: studentHeaders });
    t_student_browse.add(resClasses.timings.duration);
    check(resClasses, { 'student classes 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

    sleep(0.5);

    const resAgenda = http.get(`${BASE_URL}/api/v1/students/me/agenda`, { headers: studentHeaders });
    t_student_browse.add(resAgenda.timings.duration);
    check(resAgenda, { 'student agenda 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

    sleep(0.5);

    const resMat = http.get(`${BASE_URL}/api/v1/students/me/materials`, { headers: studentHeaders });
    t_student_materials.add(resMat.timings.duration);
    check(resMat, { 'student materials 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

  } else if (vuId < 55) {
    // ----------------------------------------------------
    // ACTIVITY GROUP 2: Student Practice Session & Quiz Flow
    // ----------------------------------------------------
    const resRec = http.post(
      `${BASE_URL}/api/v1/student/classes/${CLASS_ID}/practice-recommendations`,
      JSON.stringify({}),
      { headers: { ...studentHeaders, 'Content-Type': 'application/json' }, timeout: '10s' }
    );
    t_student_practice_rec.add(resRec.timings.duration);
    const okRec = check(resRec, { 'practice rec 200': (r) => r.status === 200 });
    if (okRec) {
      successCount.add(1);
      const session = resRec.json();
      const sid = session.sessionId;
      const questions = session.questions || [];

      if (sid && questions.length > 0) {
        sleep(0.5);
        // Answer question 0
        const q0 = questions[0];
        const ansPayload = JSON.stringify({
          questionId: q0.questionId,
          selectedOptionId: q0.options && q0.options.length > 0 ? q0.options[0].optionId : null,
          fillBlankAnswer: q0.options && q0.options.length > 0 ? null : '9.8',
        });
        const resAns = http.post(
          `${BASE_URL}/api/v1/student/practice-sessions/${sid}/answers`,
          ansPayload,
          { headers: { ...studentHeaders, 'Content-Type': 'application/json' }, timeout: '10s' }
        );
        check(resAns, { 'save answer 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

        sleep(0.5);
        // Submit session
        const resSub = http.post(
          `${BASE_URL}/api/v1/student/practice-sessions/${sid}/submit`,
          null,
          { headers: studentHeaders, timeout: '10s' }
        );
        t_student_practice_submit.add(resSub.timings.duration);
        check(resSub, { 'submit practice 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

        // View result
        const resRes = http.get(
          `${BASE_URL}/api/v1/student/practice-sessions/${sid}/results`,
          { headers: studentHeaders, timeout: '10s' }
        );
        check(resRes, { 'view results 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);
      }
    } else {
      errorRate.add(1);
    }

  } else if (vuId < 70) {
    // ----------------------------------------------------
    // ACTIVITY GROUP 3: Student Weak Topics Query
    // ----------------------------------------------------
    const resWeak = http.get(
      `${BASE_URL}/api/v1/student/classes/${CLASS_ID}/weak-topics`,
      { headers: studentHeaders, timeout: '10s' }
    );
    t_student_weak_topics.add(resWeak.timings.duration);
    check(resWeak, { 'student weak topics 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

    sleep(0.5);
    const resSched = http.get(
      `${BASE_URL}/api/v1/students/me/schedule`,
      { headers: studentHeaders, timeout: '10s' }
    );
    check(resSched, { 'student schedule 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

  } else if (vuId < 85) {
    // ----------------------------------------------------
    // ACTIVITY GROUP 4: Instructor Management & Stats
    // ----------------------------------------------------
    const resInstClasses = http.get(
      `${BASE_URL}/api/v1/instructor/classes`,
      { headers: instHeaders, timeout: '10s' }
    );
    t_instructor_classes.add(resInstClasses.timings.duration);
    check(resInstClasses, { 'instructor classes 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

    sleep(0.5);
    const resSettings = http.get(
      `${BASE_URL}/api/v1/instructor/classes/${CLASS_ID}/settings`,
      { headers: instHeaders, timeout: '10s' }
    );
    check(resSettings, { 'instructor settings 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

    sleep(0.5);
    const resWeakSummary = http.get(
      `${BASE_URL}/api/v1/instructor/classes/${CLASS_ID}/weak-topics-summary`,
      { headers: instHeaders, timeout: '10s' }
    );
    t_instructor_weak_summary.add(resWeakSummary.timings.duration);
    check(resWeakSummary, { 'instructor weak summary 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

  } else if (vuId < 95) {
    // ----------------------------------------------------
    // ACTIVITY GROUP 5: Chunked File Upload (100KB) & Merge
    // ----------------------------------------------------
    const uploadId = getRandomUUID();
    const fileName = `perf_load_${__VU}_${Date.now()}.dat`;

    const uploadPayload = {
      uploadId: uploadId,
      chunkIndex: 0,
      totalChunks: 1,
      fileName: fileName,
      chunk: http.file(chunkBytes, 'chunk_0.part', 'application/octet-stream'),
    };

    const resChunk = http.post(
      `${BASE_URL}/api/v1/files/chunks`,
      uploadPayload,
      { headers: studentHeaders, timeout: '15s' }
    );
    t_chunk_upload_100k.add(resChunk.timings.duration);
    const okChunk = check(resChunk, { 'upload chunk 200': (r) => r.status === 200 });
    if (okChunk) {
      successCount.add(1);
      sleep(0.3);

      // Check status
      const resStatus = http.get(
        `${BASE_URL}/api/v1/files/chunks/${uploadId}/status`,
        { headers: studentHeaders, timeout: '10s' }
      );
      check(resStatus, { 'chunk status 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

      sleep(0.3);
      // Merge file
      const mergePayload = JSON.stringify({
        uploadId: uploadId,
        fileName: fileName,
        totalChunks: 1,
        fileSize: 102400,
      });
      const resMerge = http.post(
        `${BASE_URL}/api/v1/files/chunks/merge`,
        mergePayload,
        { headers: { ...studentHeaders, 'Content-Type': 'application/json' }, timeout: '15s' }
      );
      t_chunk_merge.add(resMerge.timings.duration);
      check(resMerge, { 'merge chunks 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);
    } else {
      errorRate.add(1);
    }

  } else {
    // ----------------------------------------------------
    // ACTIVITY GROUP 6: Admin Management & Health
    // ----------------------------------------------------
    const resUsers = http.get(
      `${BASE_URL}/api/v1/users/admin/users?page=0&size=10`,
      { headers: adminHeaders, timeout: '10s' }
    );
    t_admin_queries.add(resUsers.timings.duration);
    check(resUsers, { 'admin users 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);

    sleep(0.5);
    const resHealth = http.get(`${BASE_URL}/actuator/health`, { timeout: '5s' });
    check(resHealth, { 'actuator health 200': (r) => r.status === 200 }) ? successCount.add(1) : errorRate.add(1);
  }

  // Think time between cycles: 1s to 2s
  sleep(1.0 + Math.random() * 1.0);
}
