import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '../api/client';
import { ThemeToggle } from '../components/ThemeToggle';

export const Register = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setIsLoading(true);
    try {
      await apiClient.post('/auth/register', { email, password });
      navigate('/login');
    } catch (err: any) {
      console.error('Registration failed', err);
      if (err.response) {
        setError(err.response.data?.message || 'Registration failed. Please try again.');
      } else if (err.request) {
        setError('Cannot reach server. Please check if the backend is running.');
      } else {
        setError('An unexpected error occurred.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const passwordsTyped = password.length > 0 && confirmPassword.length > 0;
  const passwordMismatch = passwordsTyped && password !== confirmPassword;

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-background text-foreground transition-colors">
      <div className="absolute top-4 right-4">
        <ThemeToggle />
      </div>
      <div className="w-full max-w-md p-8 bg-card text-card-foreground rounded-lg shadow-lg border border-border">
        <h1 className="text-3xl font-bold mb-6 text-center">Create Account</h1>

        {error && (
          <div className="mb-4 p-3 bg-destructive/10 border border-destructive text-destructive text-sm rounded-md">
            {error}
          </div>
        )}

        <form onSubmit={handleRegister} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium">Email</label>
            <input
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="border border-input bg-background text-foreground p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
              required
            />
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium">Password</label>
            <input
              type="password"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="border border-input bg-background text-foreground p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
              required
            />
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium">Confirm Password</label>
            <input
              type="password"
              placeholder="Confirm Password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className={`border bg-background text-foreground p-2 rounded-md focus:ring-2 outline-none transition-colors ${
                passwordMismatch
                  ? 'border-destructive focus:ring-destructive'
                  : 'border-input focus:ring-ring'
              }`}
              required
            />
            {passwordMismatch && (
              <p className="text-destructive text-xs mt-1">Passwords do not match.</p>
            )}
          </div>

          <button
            type="submit"
            disabled={isLoading || passwordMismatch}
            className="bg-primary text-primary-foreground p-2 rounded-md font-medium hover:opacity-90 transition-opacity disabled:opacity-50 mt-1"
          >
            {isLoading ? 'Creating Account...' : 'Sign Up'}
          </button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          Already have an account?{' '}
          <button onClick={() => navigate('/login')} className="text-primary hover:underline">
            Sign In
          </button>
        </p>
      </div>
    </div>
  );
};
