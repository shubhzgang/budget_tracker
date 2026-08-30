// app.js — global HTMX/Alpine setup for the Thymeleaf frontend

// Redirect to login if any HTMX request gets a 401 (expired session)
document.addEventListener('htmx:responseError', function (event) {
  if (event.detail.xhr.status === 401) {
    window.location.href = '/login?expired=true';
  }
});

// Toast notifications via HTMX HX-Trigger events
// htmx delivers the HX-Trigger payload as event.detail.value (detail.elt is the source element)
function triggerValue(e) {
  const d = e.detail;
  if (d == null) return '';
  if (typeof d === 'string') return d;
  if (d.value !== undefined) return d.value;
  return String(d);
}
document.addEventListener('toast-success', function (e) { showToast(triggerValue(e), 'success'); });
document.addEventListener('toast-error', function (e) { showToast(triggerValue(e), 'error'); });
document.addEventListener('toast-info', function (e) { showToast(triggerValue(e), 'info'); });

document.addEventListener('closeModal', function () {
  const modal = document.getElementById('modal');
  if (modal && modal.open) modal.close();
});

function showToast(message, kind) {
  const container = document.getElementById('toast-container');
  if (!container || !message) return;
  const el = document.createElement('div');
  el.className = 'toast toast-' + kind;
  el.setAttribute('role', 'status');
  el.textContent = message;
  container.appendChild(el);
  setTimeout(function () {
    el.classList.add('leaving');
    setTimeout(function () { el.remove(); }, 300);
  }, 3000);
}

window.openFormModal = function () {
  const content = document.getElementById('modal-content');
  if (content) content.innerHTML = '';
  const modal = document.getElementById('modal');
  if (modal) modal.showModal();
};

window.openAccountEdit = function (id) {
  const content = document.getElementById('modal-content');
  if (content) content.innerHTML = '';
  htmx.ajax('GET', '/accounts/' + id + '/edit', '#modal-content');
  document.getElementById('modal').showModal();
};

window.openItemEdit = function (btn) {
  const id = btn.dataset.id;
  const url = btn.dataset.kind === 'TRANSFER'
    ? '/transfers/' + id + '/edit'
    : '/transactions/' + id + '/edit';
  const content = document.getElementById('modal-content');
  if (content) content.innerHTML = '';
  htmx.ajax('GET', url, '#modal-content');
  document.getElementById('modal').showModal();
};

window.openItemDelete = function (btn) {
  const id = btn.dataset.id;
  const url = btn.dataset.kind === 'TRANSFER'
    ? '/transfers/' + id
    : '/transactions/' + id;
  const description = btn.dataset.description || 'this transaction';
  document.getElementById('confirm-title').textContent = 'Delete Transaction';
  document.getElementById('confirm-message').textContent =
    'Are you sure you want to delete "' + description + '"? This will also revert the account balance. This action cannot be undone.';
  document.getElementById('confirm-ok').onclick = function () {
    closeConfirmDialog();
    htmx.ajax('DELETE', url, { swap: 'none' });
  };
  document.getElementById('confirm-dialog').showModal();
};

window.closeConfirmDialog = function () {
  const dialog = document.getElementById('confirm-dialog');
  if (dialog && dialog.open) {
    // Clear synchronously: the dialog 'close' event fires as a task, too late for
    // same-tick assertions (e.g. Playwright strict-mode text lookups)
    document.getElementById('confirm-title').textContent = 'Confirm';
    document.getElementById('confirm-message').textContent = '';
    dialog.close();
  }
};

// Abort stale requests targeting the transactions list so the newest filter change always wins
let pendingListRequest = null;
document.addEventListener('htmx:beforeRequest', function (e) {
  const detail = e.detail || {};
  const target = detail.target;
  const id = typeof target === 'string' ? target : (target && target.id);
  if (id === 'transactions-list' || id === '#transactions-list') {
    if (pendingListRequest && pendingListRequest.readyState < 4) pendingListRequest.abort();
    pendingListRequest = detail.xhr;
  }
});

// After a transaction/transfer save or delete, refresh the current page content
document.addEventListener('refreshAfterSave', function () {
  const path = window.location.pathname;
  if (path === '/dashboard' || path.indexOf('/dashboard') === 0) {
    htmx.ajax('GET', '/dashboard/sections', {target: '#dashboard-content', swap: 'outerHTML'});
  } else if (path.indexOf('/transactions') === 0) {
    htmx.ajax('GET', '/transactions/list' + window.location.search, '#transactions-list');
  }
});

// ===== Emoji picker (Alpine component, used by the transaction form) =====
// Keep a single emoji (one grapheme cluster, ZWJ sequences included)
function sanitizeEmojiInput(raw) {
  const t = raw.trim();
  if (!t) return '';
  try {
    const segs = Array.from(new Intl.Segmenter('en', { granularity: 'grapheme' }).segment(t));
    return segs.length ? segs[0].segment : t;
  } catch (err) {
    return t;
  }
}

window.emojiPicker = function () {
  return {
    value: '😀',
    open: false,
    search: '',
    activeSection: 0,
    sections: [],
    init: function () {
      const self = this;
      fetch('/js/emojis.json')
        .then(function (r) { return r.json(); })
        .then(function (data) { self.sections = data; })
        .catch(function () {});
    },
    toggle: function () {
      this.open = !this.open;
      if (!this.open) this.search = '';
    },
    selectSection: function (i) {
      this.activeSection = i;
      const els = this.$refs.grid ? this.$refs.grid.querySelectorAll('.emoji-section') : [];
      if (els[i] && els[i].scrollIntoView) els[i].scrollIntoView({ behavior: 'smooth', block: 'start' });
    },
    onEmojiInput: function (e) {
      const clean = sanitizeEmojiInput(e.target.value);
      if (clean !== e.target.value) e.target.value = clean;
      this.value = clean;
    },
    visibleSections: function () {
      const q = this.search.trim();
      if (!q) return this.sections;
      const ql = q.toLowerCase();
      const items = [];
      for (const sec of this.sections) {
        for (const it of sec.items) {
          const tagMatch = (it.keywords || []).some(function (k) { return k.indexOf(ql) !== -1; });
          const sectionMatch = sec.label.toLowerCase().indexOf(ql) !== -1;
          if (it.emoji.indexOf(q) !== -1 || tagMatch || sectionMatch) items.push(it);
        }
      }
      return [{ label: 'Search Results', emoji: '🔍', items: items }];
    },
    pick: function (emoji) {
      this.value = emoji;
      this.open = false;
      this.search = '';
    }
  };
};

// Settings tabs: synchronous swap of the active panel (matches React conditional mounting —
// inactive tabs are unmounted; their markup waits in <template data-panel-src> elements)
document.addEventListener('click', function (e) {
  const tab = e.target && e.target.closest ? e.target.closest('.settings-tab') : null;
  if (!tab || !tab.dataset.tab) return;
  const panels = document.getElementById('settings-panels');
  if (!panels || panels.dataset.active === tab.dataset.tab) return;
  const src = document.querySelector('template[data-panel-src="' + tab.dataset.tab + '"]');
  if (!src) return;
  const section = document.createElement('section');
  section.className = 'settings-panel';
  section.dataset.panel = tab.dataset.tab;
  section.appendChild(src.content.cloneNode(true));
  while (panels.firstChild) panels.removeChild(panels.firstChild);
  panels.appendChild(section);
  if (window.htmx && htmx.process) htmx.process(section);
  panels.dataset.active = tab.dataset.tab;
  document.querySelectorAll('.settings-tab').forEach(function (t) {
    t.classList.toggle('active', t === tab);
  });
});

// Close emoji pickers on outside click (matches React mousedown-outside behavior)
document.addEventListener('mousedown', function (e) {
  const wraps = document.querySelectorAll('.emoji-picker-wrap');
  for (const wrap of wraps) {
    if (!wrap.contains(e.target)) {
      const data = window.Alpine && Alpine.$data ? Alpine.$data(wrap) : null;
      if (data && data.open) {
        data.open = false;
        data.search = '';
      }
    }
  }
}, true);

// ===== Transaction / transfer form (Alpine component) =====
window.transactionForm = function () {
  return {
    type: 'EXPENSE',
    amount: '',
    fromAmount: '',
    toAmount: '',
    adjustment: '',
    lastEdited: [],
    creating: false,
    newCatName: '',
    creatingCat: false,
    lastCategoryId: '',
    labelsOpen: false,
    selectedLabels: [],
    labels: [],

    init: function () {
      const form = this.$el;
      this.formEl = form;
      this.type = form.querySelector('[name=type]').value || 'EXPENSE';
      const readInput = function (name) {
        const el = form.querySelector('[name=' + name + ']');
        return el ? el.value : '';
      };
      this.amount = readInput('amount');
      this.fromAmount = readInput('fromAmount');
      this.toAmount = readInput('toAmount');
      this.adjustment = readInput('adjustment');
      // Read from data attributes (inline <script> in swapped fragments can run AFTER
      // Alpine initializes the x-data component — attributes are present at init time)
      try { this.labels = JSON.parse(form.getAttribute('data-labels') || '[]'); }
      catch (err) { this.labels = []; }
      try { this.selectedLabels = JSON.parse(form.getAttribute('data-initial-labels') || '[]').slice(); }
      catch (err) { this.selectedLabels = []; }
      const catSelect = form.querySelector('[data-role=category-select]');
      this.lastCategoryId = catSelect && catSelect.value !== '__new__' ? catSelect.value : '';
      // Seed 3-way calc state when editing a transfer
      if (this.type === 'TRANSFER') {
        for (const field of ['fromAmount', 'adjustment', 'toAmount']) {
          if (this[field] !== '' && this.lastEdited.length < 2) this.lastEdited.push(field);
        }
      }
      this.applyTypeUI();
      const self = this;
      form.addEventListener('htmx:confirm', function (e) {
        if (!self.validateSubmit()) {
          e.detail.issueRequest = false;
        }
      });
      document.addEventListener('mousedown', function (e) {
        if (self.labelsOpen && self.$refs.labelWrap && !self.$refs.labelWrap.contains(e.target)) {
          self.labelsOpen = false;
        }
      });
    },

    applyTypeUI: function () {
      const form = this.formEl;
      const isTransfer = this.type === 'TRANSFER';
      form.querySelector('[data-role=account-select]').name = isTransfer ? 'fromAccountId' : 'accountId';
      if (form.dataset.mode === 'create') {
        form.setAttribute('hx-post', isTransfer ? '/transfers' : '/transactions');
      }
      this.updateToAccountOptions();
    },

    onTypeChange: function () {
      this.applyTypeUI();
    },

    onAccountChange: function () {
      this.updateToAccountOptions();
    },

    updateToAccountOptions: function () {
      const form = this.formEl;
      const fromSelect = form.querySelector('[data-role=account-select]');
      const toSelect = form.querySelector('[data-role=to-account-select]');
      if (!toSelect) return;
      const fromId = fromSelect.value;
      const current = toSelect.value;
      toSelect.innerHTML = '';
      const placeholder = document.createElement('option');
      placeholder.value = '';
      placeholder.textContent = 'Select destination...';
      toSelect.appendChild(placeholder);
      for (const opt of fromSelect.options) {
        if (!opt.value || opt.value === fromId) continue;
        const option = document.createElement('option');
        option.value = opt.value;
        option.textContent = opt.textContent;
        toSelect.appendChild(option);
      }
      toSelect.value = current;
    },

    onAmountInput: function (field, value) {
      if (value !== '' && !/^\d*\.?\d*$/.test(value)) {
        const input = this.formEl.querySelector('[name=' + field + ']');
        if (input) input.value = this[field];
        return;
      }
      this[field] = value;
      if (field !== 'amount') this.computedAmount(field);
    },

    computedAmount: function (field) {
      let edited = [field].concat(this.lastEdited.filter(function (f) { return f !== field; }));
      if (edited.length > 2) edited = edited.slice(0, 2);
      this.lastEdited = edited;
      if (edited.length !== 2) return;
      const f1 = edited[0];
      const f2 = edited[1];
      const v1 = parseFloat(this[f1]);
      const v2 = parseFloat(this[f2]);
      if (isNaN(v1) || isNaN(v2)) return;
      const target = ['fromAmount', 'toAmount', 'adjustment'].find(function (f) {
        return f !== f1 && f !== f2;
      });
      let computed;
      if (target === 'adjustment') {
        const from = f1 === 'fromAmount' ? v1 : v2;
        const to = f1 === 'toAmount' ? v1 : v2;
        computed = to - from;
      } else if (target === 'toAmount') {
        const from = f1 === 'fromAmount' ? v1 : v2;
        const adj = f1 === 'adjustment' ? v1 : v2;
        computed = from + adj;
      } else {
        const to = f1 === 'toAmount' ? v1 : v2;
        const adj = f1 === 'adjustment' ? v1 : v2;
        computed = to - adj;
      }
      if (computed >= 0) {
        this[target] = parseFloat(computed.toFixed(4)).toString();
        const input = this.formEl.querySelector('[name=' + target + ']');
        if (input) input.value = this[target];
      }
    },

    onCategoryChange: function (value) {
      if (value === '__new__') {
        this.creating = true;
        this.newCatName = '';
      } else {
        this.creating = false;
        this.lastCategoryId = value;
      }
    },

    addCategory: function () {
      const name = this.newCatName.trim();
      if (!name || this.creatingCat) return;
      const form = this.formEl;
      const iconInput = form.querySelector('input[aria-label="Emoji"]');
      const icon = iconInput && iconInput.value.trim() ? iconInput.value.trim() : '😀';
      this.creatingCat = true;
      const self = this;
      fetch('/api/v1/categories', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'HX-Request': 'true' },
        body: JSON.stringify({ name: name, icon: icon })
      })
        .then(function (r) {
          if (!r.ok) {
            return r.json().then(function (d) {
              throw new Error(d.message || 'Failed to create category. Please try again.');
            }, function () {
              throw new Error('Failed to create category. Please try again.');
            });
          }
          return r.json();
        })
        .then(function (cat) {
          const select = form.querySelector('[data-role=category-select]');
          const option = document.createElement('option');
          option.value = cat.id;
          option.textContent = (cat.icon ? cat.icon + ' ' : '') + cat.name;
          const newOption = select.querySelector('option[value="__new__"]');
          select.insertBefore(option, newOption);
          select.value = cat.id;
          self.creating = false;
          self.newCatName = '';
        })
        .catch(function (e) {
          window.alert(e.message);
        })
        .finally(function () {
          self.creatingCat = false;
        });
    },

    cancelCategory: function () {
      this.creating = false;
      this.newCatName = '';
      const select = this.formEl.querySelector('[data-role=category-select]');
      if (this.lastCategoryId) {
        select.value = this.lastCategoryId;
      }
      if (select.value === '__new__' || select.value === '') {
        select.selectedIndex = 0;
      }
    },

    labelName: function (id) {
      const label = this.labels.find(function (l) { return l.id === id; });
      return label ? label.name : id;
    },

    toggleLabel: function (id) {
      const idx = this.selectedLabels.indexOf(id);
      if (idx === -1) {
        this.selectedLabels = this.selectedLabels.concat([id]);
      } else {
        this.selectedLabels = this.selectedLabels.filter(function (s) { return s !== id; });
      }
    },

    validateSubmit: function () {
      const form = this.formEl;
      if (this.creating) {
        window.alert('Please add or cancel the new category first');
        return false;
      }
      if (this.type === 'TRANSFER') {
        const readVal = function (name) {
          return parseFloat(form.querySelector('[name=' + name + ']').value);
        };
        let validCount = 0;
        if (!isNaN(readVal('fromAmount')) && readVal('fromAmount') > 0) validCount++;
        if (!isNaN(readVal('toAmount')) && readVal('toAmount') > 0) validCount++;
        if (!isNaN(readVal('adjustment')) && readVal('adjustment') >= 0) validCount++;
        if (validCount < 2) {
          window.alert('At least two of From Amount, To Amount, or Adjustment must be valid positive numbers');
          return false;
        }
        const toAccountId = form.querySelector('[name=toAccountId]').value;
        if (!toAccountId) {
          window.alert('Please select a destination account');
          return false;
        }
        const sent = this.lastEdited.length === 2 ? this.lastEdited : ['fromAmount', 'adjustment'];
        ['fromAmount', 'toAmount', 'adjustment'].forEach(function (f) {
          if (sent.indexOf(f) === -1) {
            form.querySelector('[name=' + f + ']').value = '';
          }
        });
      } else {
        const amountNum = parseFloat(form.querySelector('[name=amount]').value);
        if (isNaN(amountNum) || amountNum <= 0) {
          window.alert('Amount must be greater than zero');
          return false;
        }
      }
      return true;
    }
  };
};

// Close the shared modal / confirm dialog on backdrop click
document.addEventListener('DOMContentLoaded', function () {
  const modal = document.getElementById('modal');
  if (modal) {
    modal.addEventListener('click', function (e) {
      if (e.target === modal) modal.close();
    });
    // Unmount form content on close (matches React unmount semantics)
    modal.addEventListener('close', function () {
      const content = document.getElementById('modal-content');
      if (content) content.innerHTML = '';
    });
  }
  const confirmDialog = document.getElementById('confirm-dialog');
  if (confirmDialog) {
    confirmDialog.addEventListener('click', function (e) {
      if (e.target === confirmDialog) confirmDialog.close();
    });
  }
});
