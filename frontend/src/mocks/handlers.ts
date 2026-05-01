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

  // Mock categories
  http.get(`${API_URL}/categories`, () => {
    return HttpResponse.json([
      { id: 'c1', name: 'Food', icon: '🍔', isDefault: true, createdAt: '2026-01-01' },
      { id: 'c2', name: 'Rent', icon: '🏠', isDefault: false, createdAt: '2026-01-02' }
    ]);
  }),

  http.post(`${API_URL}/categories`, async ({ request }) => {
    const data = await request.json() as any;
    return HttpResponse.json({
      id: 'c3',
      ...data,
      isDefault: false,
      createdAt: new Date().toISOString()
    });
  }),

  http.delete(`${API_URL}/categories/:id`, () => {
    return new HttpResponse(null, { status: 204 });
  }),

  // Mock labels
  http.get(`${API_URL}/labels`, () => {
    return HttpResponse.json([
      { id: 'l1', name: 'Personal', isDefault: true, createdAt: '2026-01-01' },
      { id: 'l2', name: 'Work', isDefault: false, createdAt: '2026-01-02' }
    ]);
  }),

  http.post(`${API_URL}/labels`, async ({ request }) => {
    const data = await request.json() as any;
    return HttpResponse.json({
      id: 'l3',
      ...data,
      isDefault: false,
      createdAt: new Date().toISOString()
    });
  }),

  http.delete(`${API_URL}/labels/:id`, () => {
    return new HttpResponse(null, { status: 204 });
  }),
];
