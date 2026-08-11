(function () {
    const $ = s => document.querySelector(s);
    const status = $('#status');
    const zoom = $('#zoom');
    const zoomVal = $('#zoomVal');

    async function post(url) {
        const r = await fetch(url, { method: 'POST' });
        if (!r.ok) throw new Error(r.statusText);
        return r.json();
    }

    async function refresh() {
        try {
            const s = await (await fetch('/status')).json();
            status.textContent = `${s.width}x${s.height} @ ${s.fps}fps` +
                (s.recording ? ' • REC' : '');
            status.classList.add('ok');
            for (const btn of document.querySelectorAll('button[data-act]')) {
                const act = btn.dataset.act;
                if (act === 'torch')  btn.classList.toggle('on', !!s.torch);
                if (act === 'record') btn.classList.toggle('on', !!s.recording);
                if (act === 'wide')   btn.classList.toggle('on', s.zoom < 1);
                if (act === 'switch') btn.textContent = s.front ? 'Front cam' : 'Back cam';
            }
            if (typeof s.minZoom === 'number') zoom.min = s.minZoom;
            if (typeof s.maxZoom === 'number') zoom.max = s.maxZoom;
            zoom.value = s.zoom;
            zoomVal.textContent = Number(s.zoom).toFixed(1) + 'x';
        } catch (e) {
            status.textContent = 'disconnected';
            status.classList.remove('ok');
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

    zoom.addEventListener('input', () => {
        zoomVal.textContent = Number(zoom.value).toFixed(1) + 'x';
    });
    zoom.addEventListener('change', async () => {
        try { await post('/control/zoom?ratio=' + zoom.value); refresh(); }
        catch (e) { console.error(e); }
    });

    refresh();
    setInterval(refresh, 3000);
})();
