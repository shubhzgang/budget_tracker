export interface Category {
  id: string;
  name: string;
  icon: string;
  isDefault: boolean;
  createdAt: string;
}

export interface CreateCategoryRequest {
  name: string;
  icon: string;
}
