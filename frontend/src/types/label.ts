export interface Label {
  id: string;
  name: string;
  isDefault: boolean;
  createdAt: string;
}

export interface CreateLabelRequest {
  name: string;
}
