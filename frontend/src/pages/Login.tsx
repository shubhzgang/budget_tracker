import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import apiClient from '../api/client';
import { useNavigate } from 'react-router-dom';
import { ThemeToggle } from '../components/ThemeToggle';

export const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);
    
    try {
      const response = await apiClient.post('/auth/login', { email, password });
      login(response.data.token, { id: response.data.id, email: response.data.email });
      navigate('/dashboard');
    } catch (err: any) {
      console.error('Login failed', err);
      if (err.response) {
        setError(err.response.data?.message || 'Invalid email or password');
      } else if (err.request) {
        setError('Cannot reach server. Please check if the backend is running.');
      } else {
        setError('An unexpected error occurred');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-background text-foreground transition-colors">
      <div className="absolute top-4 right-4">
        <ThemeToggle />
      </div>
      <div className="w-full max-w-md p-8 bg-card text-card-foreground rounded-lg shadow-lg border border-border">
        <h1 className="text-3xl font-bold mb-6 text-center">Login</h1>
        
        {error && (
          <div className="mb-4 p-3 bg-destructive/10 border border-destructive text-destructive text-sm rounded-md">
            {error}
          </div>
        )}

        <form onSubmit={handleLogin} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium">Email</label>
            <input
              type="email"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
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
              className="border border-input bg-background p-2 rounded-md focus:ring-2 focus:ring-ring outline-none"
              required
            />
          </div>
          <button 
            type="submit" 
            disabled={isLoading}
            className="bg-primary text-primary-foreground p-2 rounded-md font-medium hover:opacity-90 transition-opacity disabled:opacity-50"
          >
            {isLoading ? 'Signing In...' : 'Sign In'}
          </button>
        </form>
        
        <p className="mt-4 text-center text-sm text-muted-foreground">
          Don't have an account? <button onClick={() => navigate('/register')} className="text-primary hover:underline">Sign Up</button>
        </p>
      </div>
    </div>
  );
};
