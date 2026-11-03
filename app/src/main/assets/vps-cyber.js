/* Matrix Core HUD — два режима: Монитор / Пульт, RU */
(function () {
  if (window.__mcCoreInit) {
    window.__mcCoreRefresh && window.__mcCoreRefresh();
    return;
  }
  window.__mcCoreInit = true;

  var NEON = '#39FF14';
  var DANGER = '#FF3333';
  var BORDER = '#1A8035';
  var MODE_KEY = 'mc-mode';
  // Пульт по умолчанию — чтобы сразу пользоваться Cockpit, а не смотреть на HUD
  try {
    // v3: снова форсим Пульт как стартовый экран
    if (!localStorage.getItem('mc-mode-v3')) {
      localStorage.setItem(MODE_KEY, 'control');
      localStorage.setItem('mc-mode-v3', '1');
    }
  } catch (e) {}
  var mode = localStorage.getItem(MODE_KEY) || 'control';

  function $(sel, root) {
    return (root || document).querySelector(sel);
  }
  function $all(sel, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(sel));
  }

  function frameDocs() {
    var docs = [];
    $all('iframe.container-frame, iframe').forEach(function (f) {
      try {
        if (f.contentDocument && f.contentDocument.documentElement) {
          docs.push(f.contentDocument);
        }
      } catch (e) {}
    });
    return docs;
  }

  function injectCssInto(doc) {
    if (!doc || !doc.head) return;
    var href = '/vps-mobile.css?v=19';
    var l = doc.getElementById('vps-cockpit-mobile-link');
    if (!l) {
      l = doc.createElement('link');
      l.id = 'vps-cockpit-mobile-link';
      l.rel = 'stylesheet';
      l.href = href;
      doc.head.appendChild(l);
    } else if ((l.href || '').indexOf('v=19') < 0) {
      l.href = href;
    }
    doc.documentElement.setAttribute('data-mc-theme', 'matrix-core');
    try {
      doc.body && doc.body.style.setProperty('background', '#050705', 'important');
      doc.body && doc.body.style.setProperty('color', '#39FF14', 'important');
    } catch (e) {}
  }

  function ensureOverlays() {
    if (!$('#mc-scanlines')) {
      var s = document.createElement('div');
      s.id = 'mc-scanlines';
      document.documentElement.appendChild(s);
    }
    if (!$('#mc-vignette')) {
      var v = document.createElement('div');
      v.id = 'mc-vignette';
      document.documentElement.appendChild(v);
    }
  }

  function applyMode(next) {
    mode = next === 'monitor' ? 'monitor' : 'control';
    try { localStorage.setItem(MODE_KEY, mode); } catch (e) {}
    document.documentElement.setAttribute('data-mc-mode', mode);
    var bar = $('#mc-mode-bar');
    if (bar) {
      $all('[data-mc-mode-btn]', bar).forEach(function (b) {
        b.classList.toggle('mc-active', b.getAttribute('data-mc-mode-btn') === mode);
      });
    }
  }

  function ensureModeBar() {
    if ($('#mc-mode-bar')) {
      applyMode(mode);
      return;
    }
    var bar = document.createElement('div');
    bar.id = 'mc-mode-bar';
    bar.innerHTML =
      '<button type="button" data-mc-mode-btn="monitor">МОНИТОР</button>' +
      '<button type="button" data-mc-mode-btn="control">ПУЛЬТ</button>';
    bar.addEventListener('click', function (e) {
      var b = e.target.closest('[data-mc-mode-btn]');
      if (!b) return;
      applyMode(b.getAttribute('data-mc-mode-btn'));
    });
    // Не внутрь #main.page (там CSS grid) — иначе ломается вёрстка Cockpit
    document.body.insertBefore(bar, document.body.firstChild);
    applyMode(mode);
  }

  function ensureMonitorPanel() {
    var panel = $('#mc-monitor-panel');
    if (!panel) {
      panel = document.createElement('div');
      panel.id = 'mc-monitor-panel';
    }
    // Всегда в body под mode-bar — иначе #shell Cockpit оставляет дыру внизу экрана
    if (panel.parentElement !== document.body) {
      var bar = $('#mc-mode-bar');
      if (bar && bar.parentElement === document.body && bar.nextSibling) {
        document.body.insertBefore(panel, bar.nextSibling);
      } else {
        document.body.appendChild(panel);
      }
    }
    return panel;
  }

  function parsePercent(text) {
    if (!text) return null;
    var m = String(text).replace(/\u00a0/g, ' ').match(/(\d+(?:[.,]\d+)?)\s*%/);
    if (m) return Math.min(100, Math.max(0, parseFloat(m[1].replace(',', '.'))));
    return null;
  }

  function parseRamRatio(text) {
    if (!text) return null;
    var m = String(text).replace(/\u00a0/g, ' ').match(/(\d+(?:[.,]\d+)?)\s*\/\s*(\d+(?:[.,]\d+)?)/);
    if (!m) return null;
    var a = parseFloat(m[1].replace(',', '.'));
    var b = parseFloat(m[2].replace(',', '.'));
    if (!b) return null;
    return Math.min(100, Math.max(0, (a / b) * 100));
  }

  function polar(cx, cy, r, deg) {
    var rad = ((deg - 90) * Math.PI) / 180;
    return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
  }

  function arcPath(cx, cy, r, startDeg, endDeg) {
    var start = polar(cx, cy, r, endDeg);
    var end = polar(cx, cy, r, startDeg);
    var large = endDeg - startDeg <= 180 ? 0 : 1;
    return ['M', start.x, start.y, 'A', r, r, 0, large, 0, end.x, end.y].join(' ');
  }

  function gaugeSvg(pct, danger) {
    var stroke = danger ? DANGER : NEON;
    var cx = 60, cy = 60, r = 46;
    var ticks = '';
    for (var i = 0; i < 24; i++) {
      var a = (i / 24) * 360;
      var p1 = polar(cx, cy, 52, a);
      var p2 = polar(cx, cy, i % 3 === 0 ? 44 : 48, a);
      ticks += '<line x1="' + p1.x.toFixed(2) + '" y1="' + p1.y.toFixed(2) +
        '" x2="' + p2.x.toFixed(2) + '" y2="' + p2.y.toFixed(2) +
        '" stroke="' + BORDER + '" stroke-width="' + (i % 3 === 0 ? 1.6 : 0.8) + '" />';
    }
    var track = arcPath(cx, cy, r, 0, 359.9);
    var fillLen = Math.max(0.1, Math.min(359.9, (pct / 100) * 359.9));
    var fill = arcPath(cx, cy, r, 0, fillLen);
    var brackets =
      '<path d="M14 22 L14 14 L22 14" fill="none" stroke="' + stroke + '" stroke-width="1.2"/>' +
      '<path d="M106 22 L106 14 L98 14" fill="none" stroke="' + stroke + '" stroke-width="1.2"/>' +
      '<path d="M14 98 L14 106 L22 106" fill="none" stroke="' + stroke + '" stroke-width="1.2"/>' +
      '<path d="M106 98 L106 106 L98 106" fill="none" stroke="' + stroke + '" stroke-width="1.2"/>' +
      '<circle cx="60" cy="60" r="3" fill="' + stroke + '" opacity="0.85"/>';
    return (
      '<svg viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
      '<circle cx="60" cy="60" r="56" fill="none" stroke="' + BORDER + '" stroke-width="1" opacity="0.5"/>' +
      ticks +
      '<path d="' + track + '" fill="none" stroke="' + BORDER + '" stroke-width="7" opacity="0.45"/>' +
      '<path d="' + fill + '" fill="none" stroke="' + stroke + '" stroke-width="7"/>' +
      brackets +
      '</svg>'
    );
  }

  function makeGauge(label, pct) {
    var known = pct != null && !isNaN(pct);
    var val = known ? Math.max(0, Math.min(100, pct)) : 0;
    var danger = known && val >= 85;
    var wrap = document.createElement('div');
    wrap.className = 'mc-hud-gauge' + (danger ? ' mc-danger' : '') + (known ? '' : ' mc-unknown');
    wrap.innerHTML = gaugeSvg(known ? Math.max(val, 0.1) : 0, danger) +
      '<div class="mc-value">' + (known ? Math.round(val) + '%' : '—') + '</div>' +
      '<div class="mc-label">' + label + '</div>';
    return wrap;
  }

  function waveSvg() {
    var w = 360, h = 72;
    var pts = [], pts2 = [], grid = '', x, y1, y2, gx, gy;
    for (x = 0; x <= w; x += 6) {
      y1 = 36 + Math.sin(x / 18) * 14 + Math.sin(x / 7) * 4;
      y2 = 36 + Math.cos(x / 22) * 10 + Math.sin(x / 5 + 1) * 5;
      pts.push(x + ',' + y1.toFixed(1));
      pts2.push(x + ',' + y2.toFixed(1));
    }
    for (gx = 0; gx <= w; gx += 24) {
      grid += '<line x1="' + gx + '" y1="0" x2="' + gx + '" y2="' + h + '" stroke="' + BORDER + '" stroke-width="0.5" opacity="0.45"/>';
    }
    for (gy = 0; gy <= h; gy += 18) {
      grid += '<line x1="0" y1="' + gy + '" x2="' + w + '" y2="' + gy + '" stroke="' + BORDER + '" stroke-width="0.5" opacity="0.45"/>';
    }
    return (
      '<svg viewBox="0 0 ' + w + ' ' + h + '" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="none">' +
      grid +
      '<polyline points="' + pts.join(' ') + '" fill="none" stroke="' + NEON + '" stroke-width="1.6">' +
      '<animate attributeName="points" dur="3s" repeatCount="indefinite" values="' +
      pts.join(' ') + ';' +
      pts.map(function (p, i) {
        var parts = p.split(',');
        return parts[0] + ',' + (parseFloat(parts[1]) + Math.sin(i) * 3).toFixed(1);
      }).join(' ') + ';' + pts.join(' ') + '"/>' +
      '</polyline>' +
      '<polyline points="' + pts2.join(' ') + '" fill="none" stroke="' + DANGER + '" stroke-width="1.1" opacity="0.55"/>' +
      '<text x="6" y="12" fill="' + NEON + '" font-size="9" font-family="monospace" opacity="0.8">ВХОД</text>' +
      '<text x="310" y="12" fill="' + DANGER + '" font-size="9" font-family="monospace" opacity="0.8">ВЫХОД</text>' +
      '</svg>'
    );
  }

  function collectRealHosts() {
    var hosts = [];
    function add(name) {
      name = (name || '').replace(/\s+/g, ' ').trim();
      if (!name || name.length > 80) return;
      if (/^(узел|host|localhost|system|система)$/i.test(name)) return;
      if (hosts.indexOf(name) < 0) hosts.push(name);
    }
    $all('#hosts-sel a, #nav-hosts a, .nav-hosts-menu a, [data-host]').forEach(function (a) {
      add(a.getAttribute('data-host') || a.textContent);
    });
    frameDocs().forEach(function (doc) {
      $all('a[href*="@"], [data-host]', doc).forEach(function (a) {
        add(a.getAttribute('data-host') || a.textContent);
      });
    });
    var title = document.title || '';
    var m = title.match(/@([^\s]+)/);
    if (m) add(m[1]);
    // текущий хост из шапки iframe (любой FQDN)
    frameDocs().forEach(function (doc) {
      var h = (doc.body && doc.body.innerText || '').match(/\b([a-z0-9-]+(?:\.[a-z0-9-]+)+)\b/i);
      if (h && /\./.test(h[1]) && !/^(www\.)?localhost$/i.test(h[1])) add(h[1]);
    });
    if (!hosts.length) {
      var fallback = (title.replace(/^.*@/, '').trim()) || 'текущий узел';
      add(fallback);
    }
    return hosts;
  }

  var lastMetrics = { cpu: null, ram: null, disk: null };

  function cockpitMetricFrames() {
    var frames = $all('iframe.container-frame, iframe').filter(function (f) {
      try {
        return !!(f.contentDocument && f.contentDocument.documentElement);
      } catch (e) {
        return false;
      }
    });
    // Обзор системы первым — там блок «Использование»
    frames.sort(function (a, b) {
      var sa = (a.src || '') + (a.getAttribute('name') || '');
      var sb = (b.src || '') + (b.getAttribute('name') || '');
      var score = function (s) {
        if (/\/system\/index\.html/i.test(s)) return 0;
        if (/\/system\//i.test(s)) return 1;
        return 2;
      };
      return score(sa) - score(sb);
    });
    return frames;
  }

  function scrapeDocMetrics(doc) {
    var cpu = null, ram = null, disk = null;
    if (!doc || !doc.body) return { cpu: cpu, ram: ram, disk: disk };
    // Нельзя читать наш HUD: текст «0% ЦП» отравляет парсер.
    if (doc.getElementById && doc.getElementById('mc-hud-row')) {
      return { cpu: cpu, ram: ram, disk: disk };
    }
    // innerText иногда склеивает «ЦП1%» / «Память1,3» без пробела
    var bodyText = (doc.body.innerText || '').replace(/\u00a0/g, ' ');
    // Режем наш монитор, если вдруг попал в тот же document
    var cut = bodyText.search(/\bУЗЛЫ\b|\bСЕТЬ \(СХЕМА\)\b|Метрики ЦП\/память/i);
    if (cut > 0) bodyText = bodyText.slice(0, cut);

    var cpuM = bodyText.match(/цп\s*([0-9]+(?:[.,][0-9]+)?)\s*%/i) ||
      bodyText.match(/cpu\s*([0-9]+(?:[.,][0-9]+)?)\s*%/i) ||
      bodyText.match(/цп([0-9]+(?:[.,][0-9]+)?)%/i);
    if (cpuM) cpu = parseFloat(cpuM[1].replace(',', '.'));

    var ramM = bodyText.match(/память\s*([0-9]+(?:[.,][0-9]+)?)\s*\/\s*([0-9]+(?:[.,][0-9]+)?)/i) ||
      bodyText.match(/memory\s*([0-9]+(?:[.,][0-9]+)?)\s*\/\s*([0-9]+(?:[.,][0-9]+)?)/i) ||
      bodyText.match(/память([0-9]+(?:[.,][0-9]+)?)\s*(?:гиб|gib|gb)?\s*\/\s*([0-9]+(?:[.,][0-9]+)?)/i);
    if (ramM) ram = (parseFloat(ramM[1].replace(',', '.')) / parseFloat(ramM[2].replace(',', '.'))) * 100;

    $all('.pf-v6-c-progress, .pf-c-progress, [role="progressbar"], progress', doc).forEach(function (el) {
      var block = el.closest('.pf-v6-c-card, .pf-c-card, .ct-card, section, li, div') || el.parentElement;
      var txt = ((block && block.innerText) || '').replace(/\u00a0/g, ' ');
      var pctAttr = el.getAttribute('aria-valuenow') || el.getAttribute('value');
      var pctNum = pctAttr != null && pctAttr !== '' ? parseFloat(pctAttr) : NaN;
      // Cockpit memory progress иногда отдаёт байты в aria-valuenow — игнорим гигантские числа
      var pct = (!isNaN(pctNum) && pctNum >= 0 && pctNum <= 100) ? pctNum : parsePercent(txt);
      if (pct == null || isNaN(pct)) {
        if (ram == null && /памят|memory/i.test(txt)) {
          var ratioOnly = parseRamRatio(txt);
          if (ratioOnly != null) ram = ratioOnly;
        }
        return;
      }
      if (cpu == null && /цп|cpu|процессор/i.test(txt) && !/памят|memory/i.test(txt)) cpu = pct;
      if (ram == null && /памят|memory/i.test(txt) && !/цп|cpu/i.test(txt)) {
        var ratio = parseRamRatio(txt);
        ram = ratio != null ? ratio : pct;
      }
      if (disk == null && /диск|disk|storage|хранен|filesystem/i.test(txt)) disk = pct;
    });
    return { cpu: cpu, ram: ram, disk: disk };
  }

  function findUsageMetrics() {
    var cpu = null, ram = null, disk = null;
    // Только iframe Cockpit. Shell / fallback на document — запрещены.
    cockpitMetricFrames().forEach(function (frame) {
      var m = scrapeDocMetrics(frame.contentDocument);
      if (cpu == null && m.cpu != null) cpu = m.cpu;
      if (ram == null && m.ram != null) ram = m.ram;
      if (disk == null && m.disk != null) disk = m.disk;
    });
    if (cpu != null) lastMetrics.cpu = Math.min(100, Math.max(0, cpu));
    if (ram != null) lastMetrics.ram = Math.min(100, Math.max(0, ram));
    if (disk != null) lastMetrics.disk = Math.min(100, Math.max(0, disk));
    return {
      cpu: lastMetrics.cpu,
      ram: lastMetrics.ram,
      disk: lastMetrics.disk,
      fresh: cpu != null || ram != null
    };
  }

  function fillHudRow(row, metrics) {
    row.innerHTML = '';
    row.appendChild(makeGauge('ЦП', metrics.cpu));
    row.appendChild(makeGauge('ПАМЯТЬ', metrics.ram));
    if (metrics.disk != null) row.appendChild(makeGauge('ДИСК', metrics.disk));
  }

  function mountHud(forceUpdate) {
    var panel = ensureMonitorPanel();
    var metrics = findUsageMetrics();
    var existing = $('#mc-hud-row');
    if (existing) {
      // Пересобираем при свежих данных или по таймеру (forceUpdate)
      if (!forceUpdate && !metrics.fresh) return existing;
      fillHudRow(existing, metrics);
      return existing;
    }
    var row = document.createElement('div');
    row.id = 'mc-hud-row';
    row.className = 'mc-hud-row mc-brackets';
    fillHudRow(row, metrics);
    panel.appendChild(row);
    return row;
  }

  function ensureHostMatrix() {
    var panel = ensureMonitorPanel();
    var hosts = collectRealHosts();
    var wrap = $('#mc-host-matrix-wrap');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.id = 'mc-host-matrix-wrap';
      panel.appendChild(wrap);
    }
    wrap.innerHTML =
      '<div class="mc-section-label">УЗЛЫ (' + hosts.length + ')</div>' +
      '<div class="mc-host-legend">каждый квадрат — сервер в Cockpit: зелёный = онлайн</div>' +
      '<div id="mc-host-matrix" class="mc-host-matrix"></div>';
    var grid = $('#mc-host-matrix', wrap);
    hosts.forEach(function (name) {
      var d = document.createElement('div');
      d.className = 'mc-host-dot';
      d.title = name;
      d.setAttribute('aria-label', name + ' — онлайн');
      grid.appendChild(d);
    });
  }

  function ensureWave() {
    var panel = ensureMonitorPanel();
    if ($('#mc-wave')) return;
    var box = document.createElement('div');
    box.id = 'mc-wave';
    box.className = 'mc-wave-wrap mc-brackets';
    box.innerHTML = '<div class="mc-section-label">СЕТЬ (схема)</div>' + waveSvg() +
      '<div class="mc-host-legend">декор: вход/выход трафика, не живой sniffer</div>';
    panel.appendChild(box);
  }

  function ensureTerminalTicker() {
    var panel = ensureMonitorPanel();
    if ($('#mc-term-ticker')) return;
    var host = (document.title || 'узел').replace(/^.*@/, '').trim() || 'узел';
    var lines = [
      'SSH-туннель установлен через 127.0.0.1:19090',
      'Cockpit отвечает — ' + host,
      'Метрики ЦП/память — живые',
      'Тема Matrix Core активна',
      'Режим ПУЛЬТ — полное управление сервером',
      'Режим МОНИТОР — датчики и обзор',
      'heartbeat ' + new Date().toLocaleString('ru-RU'),
      'готово.'
    ];
    var box = document.createElement('div');
    box.id = 'mc-term-ticker';
    box.className = 'mc-term-ticker';
    var inner = document.createElement('div');
    inner.className = 'mc-term-lines';
    var html = '';
    for (var k = 0; k < 2; k++) {
      lines.forEach(function (ln) {
        html += '<div class="mc-term-line">' + ln + '</div>';
      });
    }
    inner.innerHTML = html;
    box.appendChild(inner);
    panel.appendChild(box);
  }

  function ensureActionBar() {
    var panel = ensureMonitorPanel();
    if ($('#mc-actions')) return;
    var bar = document.createElement('div');
    bar.id = 'mc-actions';
    bar.className = 'mc-actions';
    bar.innerHTML =
      '<button type="button" class="mc-action-btn mc-danger" data-mc="reboot">ПЕРЕЗАГРУЗКА</button>' +
      '<button type="button" class="mc-action-btn" data-mc="ssh">ТЕРМИНАЛ</button>' +
      '<button type="button" class="mc-action-btn" data-mc="menu">МЕНЮ</button>' +
      '<button type="button" class="mc-action-btn" data-mc="to-control">В ПУЛЬТ →</button>';

    bar.addEventListener('click', function (e) {
      var btn = e.target.closest('[data-mc]');
      if (!btn) return;
      btn.classList.add('mc-glitch');
      setTimeout(function () { btn.classList.remove('mc-glitch'); }, 400);
      var kind = btn.getAttribute('data-mc');
      function findInAllDocs(sel, pred) {
        var docs = [document].concat(frameDocs());
        for (var i = 0; i < docs.length; i++) {
          var hit = $all(sel, docs[i]).filter(pred)[0];
          if (hit) return hit;
        }
        return null;
      }
      if (kind === 'to-control') {
        applyMode('control');
        return;
      }
      if (kind === 'reboot') {
        var reboot = findInAllDocs('button, a', function (el) {
          var t = (el.textContent || '').toLowerCase();
          return t.indexOf('перезагруз') >= 0 || t.indexOf('reboot') >= 0 || t.indexOf('restart') >= 0;
        });
        if (reboot) {
          applyMode('control');
          reboot.click();
        }
      } else if (kind === 'ssh') {
        var term = findInAllDocs('a, button', function (el) {
          var t = (el.textContent || '').toLowerCase();
          var href = (el.getAttribute && el.getAttribute('href')) || '';
          return t.indexOf('терминал') >= 0 || t.indexOf('terminal') >= 0 || href.indexOf('terminal') >= 0;
        });
        applyMode('control');
        if (term) term.click();
        else location.href = '/system/terminal@localhost';
      } else if (kind === 'menu') {
        applyMode('control');
        var toggle = $('#sidebar-toggle button') || $('#nav-system-item');
        if (toggle) toggle.click();
      }
    });
    panel.appendChild(bar);
  }

  function currentHostLabel() {
    var title = document.title || '';
    var m = title.match(/@([^\s]+)/);
    if (m) return m[1];
    var cur = document.querySelector('#nav-hosts a.pf-m-current, .sidebar-hosts a.pf-m-current, .nav-hosts-menu a.pf-m-current');
    if (cur) {
      var t = (cur.textContent || '').replace(/\s+/g, ' ').trim();
      if (t && !/^(обзор|overview|система)$/i.test(t)) return t;
    }
    var hosts = collectRealHosts().filter(function (h) {
      return !/^(обзор|overview|система|узел|host)$/i.test(h);
    });
    var preferred = hosts.filter(function (h) {
      return /\d+\.\d+\.\d+\.\d+/.test(h) || /\.[a-z]{2,}$/i.test(h);
    });
    return (preferred[0] || hosts[0] || 'выбрать узел');
  }

  function openHostSwitcher() {
    var btn = $('#host-toggle') || $('#hosts-sel button') || $('#hosts-sel');
    if (btn) {
      btn.click();
      return true;
    }
    return false;
  }

  function ensureControlQuickBar() {
    var bar = $('#mc-control-quick');
    if (!bar) {
      bar = document.createElement('div');
      bar.id = 'mc-control-quick';
      var content = $('#content') || document.body;
      content.insertBefore(bar, content.firstChild);
      bar.addEventListener('click', function (e) {
        var b = e.target.closest('[data-mc-q]');
        if (!b) return;
        var q = b.getAttribute('data-mc-q');
        if (q === 'monitor') applyMode('monitor');
        if (q === 'vps') openHostSwitcher();
      });
    }
    var host = currentHostLabel();
    bar.innerHTML =
      '<div class="mc-quick-row">' +
        '<button type="button" class="mc-vps-btn" data-mc-q="vps">' +
          '<small>сменить VPS / узел</small>' +
          '▾ ' + host +
        '</button>' +
        '<button type="button" data-mc-q="monitor">Монитор</button>' +
      '</div>';
  }

  function wireGlitchClicks() {
    if (document.documentElement.dataset.mcGlitch === '1') return;
    document.documentElement.dataset.mcGlitch = '1';
    document.addEventListener('click', function (e) {
      var btn = e.target.closest('button, .pf-v6-c-button, a.pf-v6-c-button, .btn');
      if (!btn || btn.closest('#mc-mode-bar')) return;
      btn.classList.add('mc-glitch');
      setTimeout(function () { btn.classList.remove('mc-glitch'); }, 380);
    }, true);
  }

  function polishMobileNav() {
    $all('#topnav .pf-v6-c-toolbar__content.pf-m-hidden, #topnav .pf-v6-c-toolbar__content[hidden]').forEach(function (el) {
      el.style.setProperty('display', 'none', 'important');
      el.style.setProperty('width', '0', 'important');
      el.style.setProperty('flex', '0 0 0', 'important');
    });
    var content = $('.ct-topnav-content');
    if (content) {
      content.style.setProperty('width', '100%', 'important');
      content.style.setProperty('flex', '1 1 100%', 'important');
      content.style.setProperty('display', 'flex', 'important');
    }
  }

  function refresh() {
    try {
      ensureOverlays();
      ensureModeBar();
      polishMobileNav();
      wireGlitchClicks();
      frameDocs().forEach(injectCssInto);
      ensureMonitorPanel();
      mountHud(false);
      ensureHostMatrix();
      ensureWave();
      ensureTerminalTicker();
      ensureActionBar();
      ensureControlQuickBar();
      applyMode(mode);
    } catch (err) {
      try { console.warn('mc-core', err); } catch (e) {}
    }
  }

  window.__mcCoreRefresh = refresh;
  window.__mcSetMode = applyMode;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', refresh);
  } else {
    refresh();
  }

  var t = null;
  var obs = new MutationObserver(function () {
    if (t) return;
    t = setTimeout(function () {
      t = null;
      refresh();
    }, 900);
  });
  obs.observe(document.documentElement, { childList: true, subtree: true });

  setInterval(function () {
    try { mountHud(true); } catch (e) {}
  }, 1500);
})();
