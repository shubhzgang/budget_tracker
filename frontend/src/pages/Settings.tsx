import React, { useState } from 'react';
import { ThemeToggle } from '../components/ThemeToggle';
import { useAuth } from '../context/AuthContext';
import { CategoryManager } from '../components/CategoryManager';
import { LabelManager } from '../components/LabelManager';
import { useNavigate } from 'react-router-dom';

type SettingsTab = 'categories' | 'labels';

export const Settings: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<SettingsTab>('categories');

  return (
    <div className="min-h-screen bg-background text-foreground transition-colors">
      <header className="border-b border-border p-4 flex justify-between items-center bg-card sticky top-0 z-10">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/dashboard')}
            className="p-2 hover:bg-secondary rounded-full transition-colors"
            aria-label="Back to Dashboard"
          >
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-5 h-5">
              <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" />
            </svg>
          </button>
          <h1 className="text-xl font-bold">Settings</h1>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-sm text-muted-foreground hidden sm:inline">{user?.email}</span>
          <ThemeToggle />
          <button
            onClick={logout}
            className="text-sm bg-destructive text-destructive-foreground hover:bg-destructive/90 px-3 py-1 rounded-md transition-colors"
          >
            Logout
          </button>
        </div>
      </header>

      <main className="p-4 sm:p-8 max-w-4xl mx-auto">
        <div className="flex border-b border-border mb-8">
          <button
            onClick={() => setActiveTab('categories')}
            className={`px-6 py-3 font-semibold text-sm transition-colors border-b-2 -mb-[1px] ${
              activeTab === 'categories'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            Categories
          </button>
          <button
            onClick={() => setActiveTab('labels')}
            className={`px-6 py-3 font-semibold text-sm transition-colors border-b-2 -mb-[1px] ${
              activeTab === 'labels'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            Labels
          </button>
        </div>

        <div className="animate-in fade-in duration-300">
          {activeTab === 'categories' ? <CategoryManager /> : <LabelManager />}
        </div>
      </main>
    </div>
  );
};
