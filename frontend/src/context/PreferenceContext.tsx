import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import apiClient from '../api/client';
import type { UserPreference, UserPreferenceRequest } from '../types/preference';
import { useAuth } from './AuthContext';

interface PreferenceContextType {
  preferences: UserPreference | null;
  isLoading: boolean;
  updatePreferences: (request: UserPreferenceRequest) => Promise<void>;
  refreshPreferences: () => Promise<void>;
}

const PreferenceContext = createContext<PreferenceContextType | undefined>(undefined);

export const PreferenceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [preferences, setPreferences] = useState<UserPreference | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const { isAuthenticated } = useAuth();

  const fetchPreferences = useCallback(async () => {
    if (!isAuthenticated) return;
    
    setIsLoading(true);
    try {
      const response = await apiClient.get<UserPreference>('/preferences');
      setPreferences(response.data);
    } catch (error) {
      console.error('Failed to fetch preferences:', error);
    } finally {
      setIsLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    fetchPreferences();
  }, [fetchPreferences]);

  const updatePreferences = async (request: UserPreferenceRequest) => {
    setIsLoading(true);
    try {
      const response = await apiClient.put<UserPreference>('/preferences', request);
      setPreferences(response.data);
    } catch (error) {
      console.error('Failed to update preferences:', error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const value = React.useMemo(() => ({
    preferences,
    isLoading,
    updatePreferences,
    refreshPreferences: fetchPreferences,
  }), [preferences, isLoading, fetchPreferences]);

  return <PreferenceContext.Provider value={value}>{children}</PreferenceContext.Provider>;
};

export const usePreferences = () => {
  const context = useContext(PreferenceContext);
  if (context === undefined) {
    throw new Error('usePreferences must be used within a PreferenceProvider');
  }
  return context;
};
