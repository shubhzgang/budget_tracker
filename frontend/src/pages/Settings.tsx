import React, { useState } from 'react';
import { CategoryManager } from '../components/CategoryManager';
import { LabelManager } from '../components/LabelManager';
import { PreferenceManager } from '../components/PreferenceManager';
import { BackupManager } from '../components/BackupManager';

type SettingsTab = 'categories' | 'labels' | 'defaults' | 'data';

export const Settings: React.FC = () => {
  const [activeTab, setActiveTab] = useState<SettingsTab>('categories');

  return (
    <div className="max-w-4xl mx-auto">
      <h2 className="text-2xl md:text-3xl font-bold mb-8">Settings</h2>
      
      <div className="flex border-b border-border mb-8 overflow-x-auto">
        <button
          onClick={() => setActiveTab('categories')}
          className={`px-6 py-3 font-semibold text-sm transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            activeTab === 'categories'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          Categories
        </button>
        <button
          onClick={() => setActiveTab('labels')}
          className={`px-6 py-3 font-semibold text-sm transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            activeTab === 'labels'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          Labels
        </button>
        <button
          onClick={() => setActiveTab('defaults')}
          className={`px-6 py-3 font-semibold text-sm transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            activeTab === 'defaults'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          Defaults
        </button>
        <button
          onClick={() => setActiveTab('data')}
          className={`px-6 py-3 font-semibold text-sm transition-colors border-b-2 -mb-[1px] whitespace-nowrap ${
            activeTab === 'data'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          Data & Backup
        </button>
      </div>

      <div className="animate-in fade-in duration-300">
        {activeTab === 'categories' && <CategoryManager />}
        {activeTab === 'labels' && <LabelManager />}
        {activeTab === 'defaults' && <PreferenceManager />}
        {activeTab === 'data' && <BackupManager />}
      </div>
    </div>
  );
};
