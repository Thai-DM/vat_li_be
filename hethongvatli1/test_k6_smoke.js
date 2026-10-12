import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  iterations: 1,
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  // 1. Health
  const resHealth = http.get(`${BASE_URL}/actuator/health`);
  check(resHealth, { 'health is 200': (r) => r.status === 200 });

  // 2. Signin
  const signinPayload = JSON.stringify({
    username: 'sv_an',
    password: 'sv_an123456',
  });
  const resSignin = http.post(`${BASE_URL}/api/v1/users/signin`, signinPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(resSignin, { 'signin is 200': (r) => r.status === 200 });
  const token = resSignin.json('data.accessToken');

  const authHeaders = {
    Authorization: `Bearer ${token}`,
  };

  // 3. Student browse classes
  const resClasses = http.get(`${BASE_URL}/api/v1/students/me/classes`, { headers: authHeaders });
  check(resClasses, { 'classes is 200': (r) => r.status === 200 });

  // 4. Upload 100KB chunk
  const chunkContent = 'A'.repeat(102400);
  const uploadId = '11111111-2222-3333-4444-555555555555';
  const data = {
    uploadId: uploadId,
    chunkIndex: 0,
    totalChunks: 1,
    fileName: 'smoke_test.dat',
    chunk: http.file(chunkContent, 'chunk_0.part'),
  };
  const resUpload = http.post(`${BASE_URL}/api/v1/files/chunks`, data, { headers: authHeaders });
  check(resUpload, { 'upload chunk is 200': (r) => r.status === 200 });

  // 5. Merge chunk
  const mergePayload = JSON.stringify({
    uploadId: uploadId,
    fileName: 'smoke_test.dat',
    totalChunks: 1,
    fileSize: 102400,
  });
  const resMerge = http.post(`${BASE_URL}/api/v1/files/chunks/merge`, mergePayload, {
    headers: { ...authHeaders, 'Content-Type': 'application/json' },
  });
  check(resMerge, { 'merge is 200': (r) => r.status === 200 });

  console.log(`Smoke test completed successfully. Status: ${resMerge.status}`);
}
