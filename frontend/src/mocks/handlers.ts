import { http, HttpResponse } from 'msw';

const API_URL = '/api/v1';

export const handlers = [
  // Mock login
  http.post(`${API_URL}/auth/login`, async ({ request }) => {
    const { email } = (await request.json()) as { email: string };

    if (email === 'error@example.com') {
      return new HttpResponse(null, { status: 401 });
    }

    return HttpResponse.json({
      token: 'mock-jwt-token',
      id: 'mock-user-id',
      email: email,
      username: 'mockuser'
    });
  }),

  // Mock fetching accounts
  http.get(`${API_URL}/accounts`, () => {
    return HttpResponse.json([
      { id: '1', name: 'Main Bank', balance: 1000, type: 'BANK' },
      { id: '2', name: 'Cash', balance: 50, type: 'CASH' }
    ]);
  }),
];
