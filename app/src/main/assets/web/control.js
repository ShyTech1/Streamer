(function () {
    const $ = s => document.querySelector(s);
    const status      = $('#status');
    const dot         = $('#motionDot');
    const zoom        = $('#zoom');
    const zoomVal     = $('#zoomVal');
    const pick        = $('#cameraPick');
    const fsBtn       = $('#fsBtn');
    const wrap        = $('#videoWrap');
    const recBadge    = $('#recBadge');
    const switchLabel = $('#switchLabel');
    const motionMag   = $('#motionMag');
    const motionMeter = $('#motionMeter');
    const sens        = $('#sens');
    const sensVal     = $('#sensVal');
    const cooldown    = $('#cooldown');
    const cooldownVal = $('#cooldownVal');
    const wnd         = $('#window');
    const windowVal   = $('#windowVal');
    const motionOnly  = $('#motionOnly');
    const motionRec   = $('#motionRec');

    async function post(url) {
        const r = await fetch(url, { method: 'POST' });
        if (!r.ok) throw new Error(r.statusText);
        return r.json();
    }

    async function loadCameras() {
        try {
            const r = await (await fetch('/cameras')).json();
            pick.innerHTML = '';
            for (const c of r.cameras || []) {
                const opt = document.createElement('option');
                opt.value = c.id;
                const face = c.facing === 0 ? 'front' : 'back';
                const label = (c.role || face);
                opt.textContent = `${label} · ${face} · ~${Math.round(c.equiv35)}mm · id ${c.id}`;
                pick.appendChild(opt);
            }
        } catch (e) { console.error(e); }
    }

    pick.addEventListener('change', async () => {
        try { await post('/control/camera?id=' + encodeURIComponent(pick.value)); refresh(); }
        catch (e) { console.error(e); }
    });

    let userDraggingZoom = false;
    let userDraggingSens = false;
    let userDraggingCd = false;
    let userDraggingWin = false;

    async function refresh() {
        try {
            const s = await (await fetch('/status')).json();
            status.textContent = `${s.width}×${s.height} · ${s.fps}fps`;
            status.classList.remove('err');
            status.classList.add('ok');

            for (const btn of document.querySelectorAll('button[data-act]')) {
                const act = btn.dataset.act;
                if (act === 'torch')  btn.classList.toggle('on', !!s.torch);
                if (act === 'record') btn.classList.toggle('on', !!s.recording);
                if (act === 'wide')   btn.classList.toggle('on', !!s.wide);
            }
            switchLabel.textContent = s.front ? 'Front' : 'Back';
            recBadge.classList.toggle('on', !!s.recording);

            if (typeof s.minZoom === 'number') zoom.min = s.minZoom;
            if (typeof s.maxZoom === 'number') zoom.max = s.maxZoom;
            if (!userDraggingZoom) {
                zoom.value = s.zoom;
                zoomVal.textContent = Number(s.zoom).toFixed(1) + '×';
            }

            if (!userDraggingSens) { sens.value = s.motionSensitivity; sensVal.textContent = s.motionSensitivity; }
            if (!userDraggingCd)   { cooldown.value = s.motionCooldownSec; cooldownVal.textContent = s.motionCooldownSec + 's'; }
            if (!userDraggingWin)  { wnd.value = s.motionWindowSec; windowVal.textContent = s.motionWindowSec + 's'; }
            motionOnly.checked = !!s.motionOnlyStream;
            motionRec.checked  = !!s.motionRecord;

            const mag = Number(s.motionMagnitude || 0);
            motionMag.textContent = mag.toFixed(1);
            const pct = Math.min(100, (mag / Math.max(1, Number(s.motionSensitivity))) * 50);
            motionMeter.style.width = pct + '%';
            dot.classList.toggle('active', !!s.motionActive);
        } catch (e) {
            status.textContent = 'disconnected';
            status.classList.remove('ok');
            status.classList.add('err');
        }
    }

    document.querySelectorAll('button[data-act]').forEach(btn => {
        btn.addEventListener('click', async () => {
            const act = btn.dataset.act;
            try {
                if (act === 'torch')  await post('/control/torch');
                if (act === 'switch') await post('/control/switch');
                if (act === 'wide')   await post('/control/wide');
                if (act === 'focus')  await post('/control/focus');
                if (act === 'record') await post('/control/record');
                refresh();
            } catch (e) { console.error(e); }
        });
    });

    zoom.addEventListener('pointerdown', () => userDraggingZoom = true);
    zoom.addEventListener('input', () => {
        zoomVal.textContent = Number(zoom.value).toFixed(1) + '×';
    });
    zoom.addEventListener('change', async () => {
        try { await post('/control/zoom?ratio=' + zoom.value); }
        catch (e) { console.error(e); }
        finally { userDraggingZoom = false; refresh(); }
    });

    function motionUpdate() {
        return post(`/control/motion?sensitivity=${sens.value}&cooldown=${cooldown.value}&window=${wnd.value}&onlyStream=${motionOnly.checked?1:0}&record=${motionRec.checked?1:0}`);
    }

    sens.addEventListener('pointerdown', () => userDraggingSens = true);
    sens.addEventListener('input', () => sensVal.textContent = sens.value);
    sens.addEventListener('change', async () => { try { await motionUpdate(); } finally { userDraggingSens = false; refresh(); } });

    cooldown.addEventListener('pointerdown', () => userDraggingCd = true);
    cooldown.addEventListener('input', () => cooldownVal.textContent = cooldown.value + 's');
    cooldown.addEventListener('change', async () => { try { await motionUpdate(); } finally { userDraggingCd = false; refresh(); } });

    wnd.addEventListener('pointerdown', () => userDraggingWin = true);
    wnd.addEventListener('input', () => windowVal.textContent = wnd.value + 's');
    wnd.addEventListener('change', async () => { try { await motionUpdate(); } finally { userDraggingWin = false; refresh(); } });

    motionOnly.addEventListener('change', async () => { await motionUpdate(); refresh(); });
    motionRec .addEventListener('change', async () => { await motionUpdate(); refresh(); });

    // collapsible sections
    document.querySelectorAll('.section-toggle').forEach(btn => {
        btn.addEventListener('click', () => {
            btn.classList.toggle('open');
            const t = document.getElementById(btn.dataset.target);
            if (t) t.classList.toggle('open');
        });
    });

    // fullscreen
    fsBtn.addEventListener('click', async () => {
        try {
            if (document.fullscreenElement) await document.exitFullscreen();
            else await wrap.requestFullscreen();
        } catch (e) { console.error(e); }
    });

    loadCameras();
    refresh();
    setInterval(refresh, 1500);
})();
