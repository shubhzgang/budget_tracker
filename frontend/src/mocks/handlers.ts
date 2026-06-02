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

  // Mock transactions
  http.get(`${API_URL}/transactions`, () => {
    return HttpResponse.json({
      content: [
        {
          id: 't1',
          amount: 50.00,
          type: 'EXPENSE',
          description: 'Grocery Shopping',
          transactionDate: new Date().toISOString(),
          accountId: '1',
          account: { id: '1', name: 'Main Bank' },
          categoryId: 'c1',
          category: { id: 'c1', name: 'Food', icon: '🍔' },
          labels: [{ id: 'l1', name: 'Personal', isDefault: true }],
          createdAt: new Date().toISOString()
        },
        {
          id: 't2',
          amount: 1000.00,
          type: 'INCOME',
          description: 'Salary',
          transactionDate: new Date().toISOString(),
          accountId: '1',
          account: { id: '1', name: 'Main Bank' },
          createdAt: new Date().toISOString()
        }
      ],
      pageable: {
        sort: { empty: false, sorted: true, unsorted: false },
        offset: 0,
        pageNumber: 0,
        pageSize: 20,
        paged: true,
        unpaged: false
      },
      last: true,
      totalPages: 1,
      totalElements: 2,
      size: 20,
      number: 0,
      sort: { empty: false, sorted: true, unsorted: false },
      first: true,
      numberOfElements: 2,
      empty: false
    });
  }),

  http.post(`${API_URL}/transactions`, async ({ request }) => {
    const data = await request.json() as any;
    return HttpResponse.json({
      id: 't3',
      ...data,
      createdAt: new Date().toISOString()
    });
  }),

  // Mock activity
  http.get(`${API_URL}/activity`, () => {
    return HttpResponse.json({
      content: [
        {
          id: 't1',
          kind: 'TRANSACTION',
          amount: 50.00,
          type: 'EXPENSE',
          description: 'Grocery Shopping',
          transactionDate: new Date().toISOString(),
          accountId: '1',
          account: { id: '1', name: 'Main Bank' },
          categoryId: 'c1',
          category: { id: 'c1', name: 'Food', icon: '🍔' },
          labels: [{ id: 'l1', name: 'Personal', isDefault: true }],
          createdAt: new Date().toISOString()
        },
        {
          id: 't2',
          kind: 'TRANSACTION',
          amount: 1000.00,
          type: 'INCOME',
          description: 'Salary',
          transactionDate: new Date().toISOString(),
          accountId: '1',
          account: { id: '1', name: 'Main Bank' },
          createdAt: new Date().toISOString()
        }
      ],
      pageable: {
        sort: { empty: false, sorted: true, unsorted: false },
        offset: 0,
        pageNumber: 0,
        pageSize: 20,
        paged: true,
        unpaged: false
      },
      last: true,
      totalPages: 1,
      totalElements: 2,
      size: 20,
      number: 0,
      sort: { empty: false, sorted: true, unsorted: false },
      first: true,
      numberOfElements: 2,
      empty: false
    });
  }),

  // Mock transfers
  http.post(`${API_URL}/transfers`, async ({ request }) => {
    const data = await request.json() as any;
    return HttpResponse.json({
      id: 'tf1',
      ...data,
      toAmount: (data.fromAmount || 0) + (data.adjustment || 0),
      createdAt: new Date().toISOString()
    });
  }),

  // Mock preferences
  http.get(`${API_URL}/preferences`, () => {
    return HttpResponse.json({
      userId: 'mock-user-id',
      defaultAccountId: '1',
      defaultTransactionType: 'EXPENSE',
      defaultCategoryId: 'c1',
      defaultLabelId: 'l1',
      currencySymbol: '₹'
    });
  }),

  http.put(`${API_URL}/preferences`, async ({ request }) => {
    const data = await request.json() as any;
    return HttpResponse.json({
      userId: 'mock-user-id',
      ...data
    });
  }),

  // Mock updating accounts
  http.put(`${API_URL}/accounts/:id`, async ({ params, request }) => {
    const data = await request.json() as any;
    return HttpResponse.json({
      id: params.id,
      ...data
    });
  }),

  // Mock updating transactions
  http.put(`${API_URL}/transactions/:id`, async ({ params, request }) => {
    const data = await request.json() as any;
    return HttpResponse.json({
      id: params.id,
      ...data
    });
  }),

  // Mock updating transfers
  http.put(`${API_URL}/transfers/:id`, async ({ params, request }) => {
    const data = await request.json() as any;
    return HttpResponse.json({
      id: params.id,
      ...data
    });
  }),
];
