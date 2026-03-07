import { createContext, useState, useEffect, type ReactNode } from 'react';
import axios from 'axios';
import { setTokenGetter } from '../api/client';

interface AuthContextType {
  token: string | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);

  // Expose token getter to the axios client
  useEffect(() => {
    setTokenGetter(() => token);
  }, [token]);

  // Listen for 401 responses from the API client
  useEffect(() => {
    const handleUnauthorized = () => setToken(null);
    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('auth:unauthorized', handleUnauthorized);
  }, []);

  const login = async (username: string, password: string) => {
    const { data } = await axios.post<{ token: string }>(
      `${import.meta.env.VITE_API_BASE_URL}/auth/login`,
      { username, password }
    );
    setToken(data.token);
  };

  const logout = () => setToken(null);

  return (
    <AuthContext.Provider value={{ token, isAuthenticated: !!token, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
