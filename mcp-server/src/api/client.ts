export type MakeClient = (baseUrl: string, jwt: string) => BudgetTrackerClient;

export const defaultMakeClient: MakeClient = (baseUrl, jwt) => new BudgetTrackerClient(baseUrl, jwt);

export class BudgetTrackerClient {
  constructor(
    private baseUrl: string,
    private jwtToken: string,
  ) {}

  private async request(method: string, path: string, body?: unknown): Promise<any> {
    const url = `${this.baseUrl.replace(/\/$/, '')}${path}`;
    const options: RequestInit = {
      method,
      headers: {
        Authorization: `Bearer ${this.jwtToken}`,
        'Content-Type': 'application/json',
      },
    };
    if (body !== undefined) options.body = JSON.stringify(body);
    const response = await fetch(url, options);
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`API error ${response.status}: ${text}`);
    }
    if (response.status === 204) return null;
    const text = await response.text();
    if (!text) return null;
    return JSON.parse(text);
  }

  async listAccounts() {
    return this.request('GET', '/api/v1/accounts');
  }
  async getAccount(id: string) {
    return this.request('GET', `/api/v1/accounts/${id}`);
  }

  async listTransactions(params: Record<string, string>) {
    const qs = new URLSearchParams(params).toString();
    return this.request('GET', `/api/v1/transactions${qs ? `?${qs}` : ''}`);
  }
  async getTransaction(id: string) {
    return this.request('GET', `/api/v1/transactions/${id}`);
  }
  async createTransaction(data: unknown) {
    return this.request('POST', '/api/v1/transactions', data);
  }
  async updateTransaction(id: string, data: unknown) {
    return this.request('PUT', `/api/v1/transactions/${id}`, data);
  }
  async deleteTransaction(id: string) {
    return this.request('DELETE', `/api/v1/transactions/${id}`);
  }

  async getExpenditureSummary() {
    return this.request('GET', '/api/v1/transactions/expenditure-summary');
  }

  async listTransfers(params: Record<string, string>) {
    const qs = new URLSearchParams(params).toString();
    return this.request('GET', `/api/v1/transfers${qs ? `?${qs}` : ''}`);
  }
  async getTransfer(id: string) {
    return this.request('GET', `/api/v1/transfers/${id}`);
  }
  async createTransfer(data: unknown) {
    return this.request('POST', '/api/v1/transfers', data);
  }
  async updateTransfer(id: string, data: unknown) {
    return this.request('PUT', `/api/v1/transfers/${id}`, data);
  }
  async deleteTransfer(id: string) {
    return this.request('DELETE', `/api/v1/transfers/${id}`);
  }

  async getActivity(params: Record<string, string>) {
    const qs = new URLSearchParams(params).toString();
    return this.request('GET', `/api/v1/activity${qs ? `?${qs}` : ''}`);
  }

  async listCategories() {
    return this.request('GET', '/api/v1/categories');
  }
  async createCategory(data: unknown) {
    return this.request('POST', '/api/v1/categories', data);
  }
  async updateCategory(id: string, data: unknown) {
    return this.request('PUT', `/api/v1/categories/${id}`, data);
  }
  async deleteCategory(id: string) {
    return this.request('DELETE', `/api/v1/categories/${id}`);
  }

  async listLabels() {
    return this.request('GET', '/api/v1/labels');
  }
}
