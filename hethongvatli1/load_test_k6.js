import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// Custom metrics
const signinSuccessRate = new Rate('signin_success_rate');
const examSubmissionTrend = new Trend('exam_submission_duration');
const rateLimitHits = new Counter('rate_limit_429_hits');

export const options = {
  stages: [
    { duration: '30s', target: 20 },  // Khởi động nhẹ: 20 VUs
    { duration: '1m',  target: 100 }, // Tải thông thường: 100 VUs đồng thời
    { duration: '30s', target: 250 }, // Tải đỉnh điểm (giờ nộp bài thi): 250 VUs
    { duration: '30s', target: 0 },   // Hạ nhiệt kết thúc
  ],
  thresholds: {
    // 95% requests phải hoàn thành dưới 500ms
    http_req_duration: ['p(95)<500', 'p(99)<1200'],
    // Tỷ lệ lỗi hệ thống (5xx) phải dưới 1%
    'http_req_failed{status:500}': ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const headers = { 'Content-Type': 'application/json' };

  // 1. Health Check
  group('01. Health Check Endpoint', function () {
    const res = http.get(`${BASE_URL}/actuator/health`);
    check(res, {
      'Health status is 200': (r) => r.status === 200,
      'Health status is UP': (r) => r.body.includes('UP'),
    });
  });

  // 2. Authentication & Rate Limiting Check
  let token = null;
  group('02. Authentication Flow (/signin)', function () {
    const loginPayload = JSON.stringify({
      username: 'sv_an',
      password: 'sv_an123456',
    });

    const res = http.post(`${BASE_URL}/api/v1/users/signin`, loginPayload, { headers });

    if (res.status === 200) {
      signinSuccessRate.add(1);
      const body = JSON.parse(res.body);
      if (body.data && body.data.accessToken) {
        token = body.data.accessToken;
      }
    } else if (res.status === 429) {
      rateLimitHits.add(1);
    } else {
      signinSuccessRate.add(0);
    }

    check(res, {
      'Signin returns 200 or 429 (Rate-limited)': (r) => r.status === 200 || r.status === 429,
    });
  });

  // 3. Forgot Password Flow
  group('03. Forgot Password Flow (/forgot-password)', function () {
    const forgotPayload = JSON.stringify({
      email: 'sv_an@email.com',
    });

    const res = http.post(`${BASE_URL}/api/v1/users/forgot-password`, forgotPayload, { headers });
    if (res.status === 429) {
      rateLimitHits.add(1);
    }

    check(res, {
      'Forgot password returns 200 or 429': (r) => r.status === 200 || r.status === 429,
    });
  });

  // 4. Authenticated Actions (Nếu login thành công)
  if (token) {
    const authHeaders = {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    };

    group('04. Student Portal Query', function () {
      // Lấy thông tin tài khoản hiện tại
      const whoamiRes = http.get(`${BASE_URL}/api/v1/users/me`, { headers: authHeaders });
      check(whoamiRes, {
        'Whoami returns 200': (r) => r.status === 200,
      });

      // Lấy danh sách học liệu Vật lý 1
      const materialsRes = http.get(`${BASE_URL}/api/v1/learning-materials`, { headers: authHeaders });
      check(materialsRes, {
        'Materials query returns 200': (r) => r.status === 200,
      });

      // Lấy danh sách 04 bài thí nghiệm ảo
      const experimentsRes = http.get(`${BASE_URL}/api/v1/experiments`, { headers: authHeaders });
      check(experimentsRes, {
        'Experiments query returns 200': (r) => r.status === 200,
      });
    });
  }

  sleep(1); // Thời gian nghỉ giữa các iteration của người dùng ảo
}
