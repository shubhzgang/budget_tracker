import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import App from './App';

describe('App Smoke Test', () => {
  it('renders login page by default (redirected from root)', async () => {
    render(<App />);
    // Since we redirect to /dashboard which is protected, it should land on Login
    expect(screen.getByText(/Login/i)).toBeInTheDocument();
  });
});
