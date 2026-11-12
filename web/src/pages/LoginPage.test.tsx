import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import App from '../App';
import { AuthProvider } from '../lib/auth';
import { ToastProvider } from '../lib/toast';

function renderLogin() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <ToastProvider>
            <App />
          </ToastProvider>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('LoginPage', () => {
  it('shows the sign-in form and both demo accounts', () => {
    renderLogin();

    expect(screen.getByRole('heading', { name: /sign in to campusone/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /office admin/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /faculty/i })).toBeInTheDocument();
  });

  it('fills the demo credentials when a role card is chosen', () => {
    renderLogin();

    fireEvent.click(screen.getByRole('button', { name: /office admin/i }));

    expect(screen.getByLabelText(/email/i)).toHaveValue('admin@campusone.demo');
    expect(screen.getByLabelText(/password/i)).toHaveValue('Campus@2026');
  });
});
