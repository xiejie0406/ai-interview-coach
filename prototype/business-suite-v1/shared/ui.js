(function () {
  'use strict';
  var previousFocus = null;
  var pendingFocus = null;
  var memory = {};
  function escape(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function icon(name) {
    return '<i data-lucide="' + escape(name) + '" aria-hidden="true"></i>';
  }
  function icons() {
    if (window.lucide) window.lucide.createIcons({ attrs: { 'aria-hidden': 'true' } });
  }
  function load(key, fallback) {
    try {
      var raw = localStorage.getItem('business-prototype-v1:' + key);
      if (raw !== null) return JSON.parse(raw);
    } catch (error) { /* file:// 的存储权限由浏览器决定，拒绝时使用内存。 */ }
    return JSON.parse(JSON.stringify(Object.prototype.hasOwnProperty.call(memory, key) ? memory[key] : fallback));
  }
  function save(key, value) {
    memory[key] = JSON.parse(JSON.stringify(value));
    try { localStorage.setItem('business-prototype-v1:' + key, JSON.stringify(value)); return true; }
    catch (error) { return false; }
  }
  function download(name, text, type) {
    var blob = text instanceof Blob ? text : new Blob([text], { type: type || 'text/plain;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 1500);
  }
  function toast(text) {
    var stack = document.querySelector('.ui-toast-stack');
    if (!stack) { stack = document.createElement('div'); stack.className = 'ui-toast-stack'; stack.setAttribute('role', 'status'); stack.setAttribute('aria-live', 'polite'); document.body.appendChild(stack); }
    var item = document.createElement('div'); item.className = 'ui-toast'; item.innerHTML = icon('circle-check') + '<span>' + escape(text) + '</span>'; stack.appendChild(item); icons();
    setTimeout(function () { item.remove(); }, 4200);
  }
  function closePanel(restore) {
    clearTimeout(pendingFocus);
    var panel = document.querySelector('.ui-overlay');
    if (panel) panel.remove();
    document.body.classList.remove('ui-locked');
    if (restore !== false && previousFocus && previousFocus.isConnected) previousFocus.focus();
  }
  function panel(kind, title, body, footer) {
    var existing = document.querySelector('.ui-overlay');
    if (!existing) previousFocus = document.activeElement;
    closePanel(false);
    var overlay = document.createElement('div');
    var isDrawer = kind === 'drawer';
    overlay.className = 'ui-overlay' + (isDrawer ? ' drawer-overlay' : '');
    overlay.innerHTML = '<section id="ui-' + kind + '" class="ui-panel' + (isDrawer ? ' ui-drawer' : '') + '" role="dialog" aria-modal="true" aria-labelledby="ui-panel-title" tabindex="-1"><header class="ui-panel-header"><h2 id="ui-panel-title">' + escape(title) + '</h2><button type="button" class="icon-btn" data-close-' + kind + ' aria-label="关闭" title="关闭">' + icon('x') + '</button></header><div class="ui-panel-body">' + body + '</div>' + (footer ? '<footer class="ui-panel-footer">' + footer + '</footer>' : '') + '</section>';
    document.body.appendChild(overlay); document.body.classList.add('ui-locked'); icons();
    pendingFocus = setTimeout(function () {
      var first = overlay.querySelector('[autofocus],input:not([type=hidden]),textarea,select,button');
      if (first) first.focus();
    }, 40);
    return overlay.querySelector('.ui-panel');
  }
  document.addEventListener('click', function (event) {
    var target = event.target;
    if (!(target instanceof Element)) return;
    if (target.closest('[data-close-modal],[data-close-drawer]')) closePanel();
    if (target.closest('[data-toggle-nav]')) {
      document.body.classList.toggle('nav-open');
      var backdrop = document.querySelector('.ui-menu-backdrop');
      if (!backdrop) { backdrop = document.createElement('button'); backdrop.className = 'ui-menu-backdrop'; backdrop.setAttribute('aria-label', '关闭导航'); document.body.appendChild(backdrop); }
      backdrop.classList.toggle('visible', document.body.classList.contains('nav-open'));
    }
    if (target.closest('.ui-menu-backdrop,.sidebar .nav-item')) {
      document.body.classList.remove('nav-open');
      document.querySelectorAll('.ui-menu-backdrop').forEach(function (item) { item.classList.remove('visible'); });
    }
  });
  document.addEventListener('keydown', function (event) {
    var overlay = document.querySelector('.ui-overlay');
    if (event.key === 'Escape') {
      if (overlay) closePanel();
      document.body.classList.remove('nav-open');
      document.querySelectorAll('.ui-menu-backdrop').forEach(function (item) { item.classList.remove('visible'); });
    }
    if (event.key === 'Tab' && overlay) {
      var focusable = Array.from(overlay.querySelectorAll('button:not(:disabled),a[href],input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]')).filter(function (el) { return el.getClientRects().length > 0; });
      var first = focusable[0], last = focusable[focusable.length - 1];
      if (!first) { event.preventDefault(); return; }
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  });
  window.UI = { escape: escape, icon: icon, icons: icons, load: load, save: save, download: download, toast: toast,
    modal: function (title, body, footer) { return panel('modal', title, body, footer); },
    drawer: function (title, body, footer) { return panel('drawer', title, body, footer); },
    closeModal: closePanel, closeDrawer: closePanel };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', icons); else icons();
}());
