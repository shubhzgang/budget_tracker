import React, { createContext, useContext, useState, useCallback } from 'react';

interface UIContextType {
  isTransactionModalOpen: boolean;
  openTransactionModal: () => void;
  closeTransactionModal: () => void;
  refreshTrigger: number;
  triggerRefresh: () => void;
}

const UIContext = createContext<UIContextType | undefined>(undefined);

export const UIProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isTransactionModalOpen, setIsTransactionModalOpen] = useState(false);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const openTransactionModal = useCallback(() => setIsTransactionModalOpen(true), []);
  const closeTransactionModal = useCallback(() => setIsTransactionModalOpen(false), []);
  const triggerRefresh = useCallback(() => setRefreshTrigger(prev => prev + 1), []);

  return (
    <UIContext.Provider value={{
      isTransactionModalOpen,
      openTransactionModal,
      closeTransactionModal,
      refreshTrigger,
      triggerRefresh
    }}>
      {children}
    </UIContext.Provider>
  );
};

/* eslint-disable react-refresh/only-export-components */
export const useUI = () => {
  const context = useContext(UIContext);
  if (context === undefined) {
    throw new Error('useUI must be used within a UIProvider');
  }
  return context;
};
