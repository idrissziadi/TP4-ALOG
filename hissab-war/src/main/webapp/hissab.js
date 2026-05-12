// HISSAB — Logique JavaScript côté client
const API_BASE   = 'api/hissab';
const PAGE_SIZE  = 10;
let   histData   = [];
let   currentPage = 1;

// Chargement de l'historique au démarrage
document.addEventListener('DOMContentLoaded', chargerHistorique);

function fichierChoisi(input) {
    const zone  = document.getElementById('uploadZone');
    const label = document.getElementById('uploadLabel');

    // Supprimer l'éventuelle prévisualisation précédente
    const ancienne = document.getElementById('filePreview');
    if (ancienne) ancienne.remove();

    if (input.files && input.files[0]) {
        const file = input.files[0];
        zone.classList.add('has-file');
        label.textContent = '✓ ' + file.name;

        // Prévisualisation pour les images (PNG, JPG, etc.)
        if (file.type.startsWith('image/')) {
            const img = document.createElement('img');
            img.id        = 'filePreview';
            img.className = 'file-preview';
            const url = URL.createObjectURL(file);
            img.src = url;
            img.onload = () => URL.revokeObjectURL(url);
            zone.insertBefore(img, zone.querySelector('.upload-text'));
        }
    } else {
        zone.classList.remove('has-file');
        label.textContent = 'Déposer un fichier ou cliquer pour parcourir';
    }
}

async function verifier() {
    const btn        = document.getElementById('btnVerifier');
    const fichier    = document.getElementById('fichierInput').files[0];
    const expression = document.getElementById('expressionInput').value.trim();

    if (!fichier && !expression) {
        afficherErreur('Veuillez fournir une image ou saisir une expression.');
        return;
    }

    btn.disabled     = true;
    btn.textContent  = '⏳ Calcul en cours...';
    masquerResultat();

    try {
        const formData = new FormData();
        if (fichier)     formData.append('fichier',     fichier);
        if (expression)  formData.append('expression',  expression);

        const response = await fetch(API_BASE + '/calculer', {
            method: 'POST',
            body:   formData
        });

        const data = await response.json();

        if (data.succes) {
            afficherSucces(data.expression, data.resultat);
            chargerHistorique();
        } else {
            afficherErreur(data.message || 'Erreur inconnue.');
        }
    } catch (err) {
        afficherErreur('Erreur de connexion au serveur : ' + err.message);
    } finally {
        btn.disabled    = false;
        btn.textContent = '✅ Vérifier le calcul';
    }
}

function afficherSucces(expression, resultat) {
    const box      = document.getElementById('resultatBox');
    const exprElem = document.getElementById('resultatExpression');
    const valElem  = document.getElementById('resultatValeur');
    const errElem  = document.getElementById('resultatErreur');

    const valFormatee = parseFloat(resultat.toFixed(6)).toString();

    box.className          = 'resultat-box succes';
    box.style.display      = 'block';
    exprElem.textContent   = '🔢 Expression : ' + expression;
    valElem.textContent    = '= ' + valFormatee;
    errElem.textContent    = '';
}

function afficherErreur(message) {
    const box     = document.getElementById('resultatBox');
    const exprElem = document.getElementById('resultatExpression');
    const valElem  = document.getElementById('resultatValeur');
    const errElem  = document.getElementById('resultatErreur');

    box.className        = 'resultat-box erreur';
    box.style.display    = 'block';
    exprElem.textContent = '';
    valElem.textContent  = '';
    errElem.textContent  = '❌ ' + message;
}

function masquerResultat() {
    document.getElementById('resultatBox').style.display = 'none';
}

async function chargerHistorique() {
    const container = document.getElementById('historiqueContainer');
    try {
        const response = await fetch(API_BASE + '/historique');
        const data     = await response.json();

        if (!data || data.length === 0) {
            histData = [];
            container.innerHTML = '<p class="empty-msg">Aucun calcul enregistré.</p>';
            document.getElementById('paginationContainer').innerHTML = '';
            return;
        }

        histData = data;
        currentPage = 1;
        renderPage(1);

    } catch (err) {
        container.innerHTML = '<p class="empty-msg">Impossible de charger l\'historique.</p>';
    }
}

function renderPage(page) {
    currentPage = page;
    const container  = document.getElementById('historiqueContainer');
    const total      = histData.length;
    const totalPages = Math.ceil(total / PAGE_SIZE);
    const start      = (page - 1) * PAGE_SIZE;
    const end        = Math.min(start + PAGE_SIZE, total);
    const slice      = histData.slice(start, end);

    let html = `<div class="hist-table-wrap">
            <table>
                <thead>
                    <tr>
                        <th class="col-num">#</th>
                        <th class="col-expr">Expression</th>
                        <th class="col-res">Résultat</th>
                        <th class="col-date">Date</th>
                    </tr>
                </thead>
                <tbody>`;

    slice.forEach((item, idx) => {
        // MOXy (GlassFish) ajoute "[UTC]" non reconnu par JS → on le retire
        const rawDate   = item.dateCalcul ? item.dateCalcul.replace(/\[.*?\]$/, '') : null;
        const date      = rawDate ? new Date(rawDate).toLocaleString('fr-FR') : '—';
        const resultat  = parseFloat(item.resultat.toFixed(6)).toString();
        const globalNum = start + idx + 1;
        const delay     = idx * 35;

        html += `<tr style="animation: slideUp .3s ${delay}ms ease both">
                    <td class="col-num"><span class="row-num">${globalNum}</span></td>
                    <td class="col-expr" title="${escapeHtml(item.expression)}">${escapeHtml(item.expression)}</td>
                    <td class="col-res"><span class="badge-resultat">${resultat}</span></td>
                    <td class="col-date">${date}</td>
                </tr>`;
    });

    html += '</tbody></table></div>';
    container.innerHTML = html;

    renderPagination(totalPages);
}

function renderPagination(totalPages) {
    const nav = document.getElementById('paginationContainer');

    if (totalPages <= 1) {
        nav.innerHTML = '';
        return;
    }

    const hasPrev = currentPage > 1;
    const hasNext = currentPage < totalPages;

    nav.innerHTML = `<div class="pagination">
        <button class="pg-btn" onclick="renderPage(${currentPage - 1})" ${hasPrev ? '' : 'disabled'}>← Préc</button>
        <span class="pg-info">${currentPage} <span class="pg-sep">/</span> ${totalPages}</span>
        <button class="pg-btn" onclick="renderPage(${currentPage + 1})" ${hasNext ? '' : 'disabled'}>Suiv →</button>
    </div>`;
}

function escapeHtml(text) {
    return String(text)
        .replace(/&/g,  '&amp;')
        .replace(/</g,  '&lt;')
        .replace(/>/g,  '&gt;')
        .replace(/"/g,  '&quot;');
}
