import { useEffect, useState, useCallback, useMemo, useRef } from 'react'
import { login, register, decode, client } from './api'

const GW = 'http://localhost:8090'

// Explains why the backend is unreachable, so the page never fails silently
async function diagnose(token) {
  try {
    const r = await fetch(GW + '/api/flow/types', { headers: { Authorization: 'Bearer ' + token } })
    if (r.ok) return ''
    if (r.status === 404) return 'The gateway has no route for /api/flow (HTTP 404). Check the gateway routes and restart it.'
    if (r.status === 502 || r.status === 503) return 'The gateway cannot reach flow-service (HTTP ' + r.status + '). Start flow-service on port 8087.'
    if (r.status === 401) return 'The login token was rejected (HTTP 401). Check that each service uses jwk-set-uri http://localhost:8089/.well-known/jwks.json and that identity-service is running.'
    if (r.status === 403) return 'Your account has no organization (HTTP 403). Sign out and sign in again.'
    return 'The server answered HTTP ' + r.status + '.'
  } catch { return 'Cannot reach the gateway at ' + GW + '. Start it, and open this app at http://localhost:5173 so CORS allows it.' }
}

export default function App() {
  const [session, setSession] = useState(null) // { token, mustChange }, kept in memory only, never in localStorage
  if (!session) return <Login onSession={setSession} />
  if (session.mustChange) return <ChoosePassword token={session.token} onDone={() => setSession({ ...session, mustChange: false })} signOut={() => setSession(null)} />
  return <Shell token={session.token} signOut={() => setSession(null)} />
}

function Login({ onSession }) {
  const [mode, setMode] = useState('signin')
  const [f, setF] = useState({ username: '', password: '', organizationName: '', email: '', displayName: '' })
  const [err, setErr] = useState(''), [busy, setBusy] = useState(false)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  const signup = mode === 'register'
  const ready = f.username && f.password && (!signup || (f.organizationName && f.email))
  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setErr('')
    try {
      const r = signup
        ? await register({ organizationName: f.organizationName, username: f.username, email: f.email, displayName: f.displayName, password: f.password })
        : await login(f.username, f.password)
      onSession({ token: r.accessToken, mustChange: r.mustChangePassword })
    } catch (x) { setErr(x.message) } finally { setBusy(false) }
  }
  return (
    <div className="login">
      <form onSubmit={submit}>
        <h1>Tender Track</h1>
        <p className="mute">{signup ? 'Create your organization. You become its administrator.' : 'Sign in to your organization’s workspace.'}</p>
        {signup && <><label htmlFor="o">Organization name</label><input id="o" value={f.organizationName} onChange={set('organizationName')} /></>}
        <label htmlFor="u">Username</label><input id="u" value={f.username} onChange={set('username')} autoComplete="username" />
        {signup && <><label htmlFor="e">Email</label><input id="e" type="email" value={f.email} onChange={set('email')} /><label htmlFor="d">Your name (optional)</label><input id="d" value={f.displayName} onChange={set('displayName')} /></>}
        <label htmlFor="p">Password</label><input id="p" type="password" value={f.password} onChange={set('password')} autoComplete={signup ? 'new-password' : 'current-password'} />
        {signup && <p className="mute small">At least 8 characters, with a letter and a number.</p>}
        {err && <p className="err" role="alert">{err}</p>}
        <button className="b p" disabled={busy || !ready}>{busy ? 'Please wait…' : signup ? 'Create organization' : 'Sign in'}</button>
        <button type="button" className="b" onClick={() => { setMode(signup ? 'signin' : 'register'); setErr('') }}>{signup ? 'I already have an account' : 'Create a new organization'}</button>
      </form>
    </div>
  )
}

function PasswordForm({ api, notify, onDone }) {
  const [cur, setCur] = useState(''), [nw, setNw] = useState(''), [again, setAgain] = useState('')
  const save = () => {
    if (!cur || !nw) return notify('Enter your current and new password')
    if (nw !== again) return notify('The new passwords do not match')
    api('PUT', '/api/me/password', { currentPassword: cur, newPassword: nw })
      .then(() => { notify('Password changed'); setCur(''); setNw(''); setAgain(''); if (onDone) onDone() })
      .catch((e) => notify(e.message))
  }
  return (<div className="form" style={{ maxWidth: 520 }}>
    <div><label htmlFor="pc">Current password</label><input id="pc" type="password" value={cur} onChange={(e) => setCur(e.target.value)} autoComplete="current-password" /></div>
    <div><label htmlFor="pn">New password</label><input id="pn" type="password" value={nw} onChange={(e) => setNw(e.target.value)} autoComplete="new-password" /></div>
    <div><label htmlFor="pa">Repeat new password</label><input id="pa" type="password" value={again} onChange={(e) => setAgain(e.target.value)} autoComplete="new-password" /></div>
    <div><button className="b p" onClick={save}>Change password</button></div>
  </div>)
}

function ChoosePassword({ token, onDone, signOut }) {
  const api = useMemo(() => client(token, signOut), [token])
  const [msg, setMsg] = useState('')
  return (
    <div className="login">
      <div style={{ background: 'var(--panel)', border: '1px solid var(--line)', borderRadius: 14, padding: 28, width: 'min(560px,100%)' }}>
        <h1>Choose a new password</h1>
        <p className="mute">An administrator set your first password. Choose your own before you continue.</p>
        <PasswordForm api={api} notify={setMsg} onDone={onDone} />
        {msg && <p className="err" role="alert">{msg}</p>}
        <p><button className="b" onClick={signOut}>Sign out</button></p>
      </div>
    </div>
  )
}

function Shell({ token, signOut }) {
  const claims = useMemo(() => decode(token), [token])
  const roles = new Set((claims.realm_access?.roles || []).map((r) => r.toUpperCase()))
  const me = { admin: roles.has('ADMIN'), coord: roles.has('COORDINATOR'), viewer: roles.has('VIEWER') }
  const api = useMemo(() => client(token, signOut), [token])
  const [cfg, setCfg] = useState({ displayName: '', timeZone: 'UTC', queues: [], sites: [] })
  const [types, setTypes] = useState([])
  const [view, setView] = useState(null), [msg, setMsg] = useState('')
  const [loaded, setLoaded] = useState(false), [problem, setProblem] = useState('')
  const timer = useRef()
  const notify = (m) => { setMsg(m); clearTimeout(timer.current); timer.current = setTimeout(() => setMsg(''), 2500) }
  const reload = useCallback(() => {
    setProblem('')
    Promise.all([api('GET', '/api/tenant/settings').then(setCfg).catch(() => null), api('GET', '/api/flow/types').then(setTypes)])
      .catch(async () => setProblem((await diagnose(token)) || 'Could not load your workflows. Try again.'))
      .finally(() => setLoaded(true))
  }, [api, token])
  useEffect(() => { reload() }, [reload])
  const [unread, setUnread] = useState(0)
  const loadUnread = useCallback(() => api('GET', '/api/flow/reminders/count').then((r) => setUnread(r.unread)).catch(() => {}), [api])
  useEffect(() => { loadUnread(); const t = setInterval(loadUnread, 300000); return () => clearInterval(t) }, [loadUnread])

  const orgName = cfg.displayName || claims.org_name || 'Your organization'
  const tenderType = types.find((t) => t.key === 'tender')
  const tabs = [...(tenderType ? [['dashboard', 'Dashboard']] : []), ...types.map((t) => ['flow:' + t.key, t.name]), ...(types.length ? [['reminders', unread ? `Reminders (${unread})` : 'Reminders']] : []), ...(me.admin ? [['users', 'Users'], ['audit', 'Audit log'], ['settings', 'Settings']] : []), ['account', 'My account']]
  const fallback = tenderType ? 'dashboard' : types[0] ? 'flow:' + types[0].key : me.admin ? 'settings' : 'empty'
  const current = view && tabs.some((t) => t[0] === view) ? view : fallback
  const roleLabel = [...roles].filter((r) => ['ADMIN', 'COORDINATOR', 'VIEWER'].includes(r)).join(', ').toLowerCase()
  return (
    <div className="app">
      <aside>
        <div className="logo">Tender Track</div>
        <div className="tenant"><div className="mark">{orgName.split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase()}</div>
          <div><b>{orgName}</b><span>{claims.preferred_username}{roleLabel ? ' · ' + roleLabel : ''}</span></div></div>
        <nav aria-label="Main">{tabs.map(([k, l]) => <button key={k} aria-current={current === k ? 'page' : undefined} onClick={() => setView(k)}>{l}</button>)}</nav>
        <button className="b" onClick={signOut}>Sign out</button>
      </aside>
      <main>
        {problem && <div role="alert" style={{ display: 'flex', gap: 12, alignItems: 'center', justifyContent: 'space-between', border: '1px solid var(--bad)', borderRadius: 10, padding: '10px 14px', marginBottom: 16 }}><span>{problem}</span><button className="b" onClick={reload}>Retry</button></div>}
        {!loaded ? <p className="sub">Loading…</p> : <>
        {current === 'dashboard' && tenderType && <Dashboard key="dashboard" api={api} notify={notify} type={tenderType} goTo={setView} />}
        {current.startsWith('flow:') && types.find((t) => 'flow:' + t.key === current) && <Flow key={current} api={api} token={token} me={me} notify={notify} type={types.find((t) => 'flow:' + t.key === current)} />}
        {current === 'reminders' && <Reminders api={api} notify={notify} goTo={setView} onChange={loadUnread} />}
        {current === 'users' && <Users api={api} notify={notify} myName={claims.preferred_username} />}
        {current === 'account' && <Account api={api} notify={notify} />}
        {current === 'audit' && <Audit api={api} notify={notify} />}
        {current === 'settings' && <Settings api={api} notify={notify} cfg={cfg} setCfg={setCfg} types={types} setTypes={setTypes} onInstalled={setView} />}
        {current === 'empty' && <><h1>Welcome</h1><p className="sub">No workflows are set up for your organization yet. Ask an administrator to install one in Settings.</p></>}
        </>}
      </main>
      <div id="toast" className={msg ? 'on' : ''} role="status" aria-live="polite">{msg}</div>
    </div>
  )
}

function useLoad(api, path, notify) {
  const [rows, setRows] = useState([])
  const load = useCallback(() => api('GET', path).then(setRows).catch((e) => notify(e.message)), [api, path])
  useEffect(() => { load() }, [load])
  return [rows, load]
}

function Audit({ api, notify }) {
  const [rows] = useLoad(api, '/api/audit/log', notify)
  return (<>
    <h1>Audit log</h1>
    <p className="sub">Every change in your organization and who made it. Other organizations never appear here.</p>
    <Table head={['When', 'Person', 'Action']} empty="No activity yet. Create or approve a tender to see entries here." rows={rows.map((l) => [
      <span className="mute">{new Date(l.at).toLocaleString()}</span>, l.actor, l.action.replaceAll('_', ' ').toLowerCase()])} />
  </>)
}

function Settings({ api, notify, cfg, setCfg, types, setTypes, onInstalled }) {
  const [name, setName] = useState(cfg.displayName), [email, setEmail] = useState('')
  useEffect(() => { setName(cfg.displayName) }, [cfg])
  useEffect(() => { api('GET', '/api/notifications/settings').then((s) => setEmail(s.escalationEmail)).catch((e) => notify(e.message)) }, [api])
  const install = (key) => api('POST', `/api/flow/templates/${key}/install`).then(() => api('GET', '/api/flow/types')).then((t) => { setTypes(t); notify('Template installed'); onInstalled('flow:' + key) }).catch((e) => notify(e.message))
  const upgradeAll = async () => {
    try { for (const t of types) await api('PUT', `/api/flow/templates/${t.key}/upgrade`); setTypes(await api('GET', '/api/flow/types')); notify('Workflows updated') } catch (e) { notify(e.message) }
  }
  const saveOrg = () => api('PUT', '/api/tenant/settings', { displayName: name, timeZone: cfg.timeZone || 'UTC', queues: cfg.queues.length ? cfg.queues : ['General'], sites: cfg.sites }).then((c) => { setCfg(c); notify('Saved') }).catch((e) => notify(e.message))
  const saveMail = () => api('PUT', '/api/notifications/settings', { escalationEmail: email }).then(() => notify('Saved')).catch((e) => notify(e.message))
  return (<>
    <h1>Settings</h1>
    <p className="sub">These apply to everyone in your organization.</p>
    <h2>Organization</h2>
    <div className="add" style={{ gridTemplateColumns: '2fr auto' }}>
      <div><label htmlFor="dn">Display name</label><input id="dn" value={name} onChange={(e) => setName(e.target.value)} placeholder="Your company name" /></div>
      <button className="b p" onClick={saveOrg}>Save name</button>
    </div>
    <h2 style={{ marginTop: 28 }}>Workflows</h2>
    <p className="sub">Add a ready-made workflow. It appears as a new tab for everyone in your organization.</p>
    <div className="chips">{AVAILABLE.map((t) => types.some((x) => x.key === t.key) ? <span key={t.key} className="pill ok">{t.name} installed</span> : <button key={t.key} className="b p" onClick={() => install(t.key)}>Install {t.name.toLowerCase()}</button>)}</div>
    {types.length > 0 && <p><button className="b" onClick={upgradeAll}>Update installed workflows to the latest version</button> <span className="mute small">Adds new fields and reminder rules. Your records are kept.</span></p>}
    <h2 style={{ marginTop: 28 }}>Deadline alerts</h2>
    <p className="sub">When an open tender is due within 3 days, this address gets an email. Messages never include bid details.</p>
    <div className="add" style={{ gridTemplateColumns: '2fr auto' }}>
      <div><label htmlFor="em">Alert email</label><input id="em" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="manager@yourcompany.com" /></div>
      <button className="b p" onClick={saveMail}>Save alert email</button>
    </div>
  </>)
}

function FieldInput({ fd, value, onChange }) {
  if (fd.type === 'checklist') return <div>{fd.options.map((o) => <label key={o} className="chk"><input type="checkbox" checked={(value || []).includes(o)} onChange={() => onChange((value || []).includes(o) ? value.filter((x) => x !== o) : [...(value || []), o])} />{o}</label>)}</div>
  if (fd.type === 'select') return <select value={value ?? fd.options[0]} onChange={(e) => onChange(e.target.value)}>{fd.options.map((o) => <option key={o}>{o}</option>)}</select>
  return <input type={fd.type === 'number' ? 'number' : fd.type === 'date' ? 'date' : 'text'} value={value ?? ''} onChange={(e) => onChange(e.target.value)} />
}

function Flow({ api, token, me, notify, type }) {
  const def = type.definition
  const [rows, load] = useLoad(api, `/api/flow/cases?type=${type.key}`, notify)
  const blank = () => Object.fromEntries(def.fields.map((f) => [f.key, f.type === 'checklist' ? [] : f.type === 'select' ? f.options[0] : '']))
  const [f, setF] = useState(blank), [open, setOpen] = useState(null), [edit, setEdit] = useState({})
  const canEdit = me.admin || me.coord
  const create = () => {
    const miss = def.fields.find((x) => x.required && !String(f[x.key] ?? '').trim())
    if (miss) return notify('Missing: ' + miss.label)
    api('POST', '/api/flow/cases', { typeKey: type.key, data: f }).then(() => { notify('Created'); setF(blank()); load() }).catch((e) => notify(e.message))
  }
  const go = (id, to) => api('POST', `/api/flow/cases/${id}/transition`, { to }).then(() => { notify('Updated'); load() }).catch((e) => notify(e.message))
  const save = (id) => api('PUT', `/api/flow/cases/${id}/data`, edit).then(() => { notify('Saved'); load() }).catch((e) => notify(e.message))
  const today = new Date().toISOString().slice(0, 10)
  const [q, setQ] = useState(''), [stage, setStage] = useState(''), [due, setDue] = useState('')
  const items = (def.fields.find((x) => x.type === 'checklist') || {}).options || []
  const ready = (c) => (c.data.checklist || []).length + '/' + items.length
  const shown = rows.filter((c) => {
    if (q && !(c.title + ' ' + (c.data.authority || '') + ' ' + (c.data.issuer || '')).toLowerCase().includes(q.toLowerCase())) return false
    if (stage && c.status !== stage) return false
    if (due) {
      if (!c.open || !c.deadline) return false
      const n = daysFromToday(c.deadline)
      if (due === 'overdue' ? n >= 0 : n < 0 || n > Number(due)) return false
    }
    return true
  }).sort(byDeadline)
  return (<>
    <h1>{type.name}</h1>
    <p className="sub">Each step follows rules set by this workflow. Steps marked for approval need a different person from the one who created the case.</p>
    {canEdit && <div className="form">{def.fields.map((fd) => <div key={fd.key} style={fd.type === 'checklist' ? { gridColumn: '1 / -1' } : undefined}><label>{fd.label}{fd.required ? ' *' : ''}</label><FieldInput fd={fd} value={f[fd.key]} onChange={(v) => setF({ ...f, [fd.key]: v })} /></div>)}<div><button className="b p" onClick={create}>Create</button></div></div>}
    <div className="tools">
      <div style={{ flex: '1 1 220px' }}><label htmlFor="q">Search</label><input id="q" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Title or authority" /></div>
      <div><label htmlFor="st">Stage</label><select id="st" value={stage} onChange={(e) => setStage(e.target.value)}><option value="">All stages</option>{def.states.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}</select></div>
      <div><label htmlFor="du">Deadline</label><select id="du" value={due} onChange={(e) => setDue(e.target.value)}><option value="">Any time</option><option value="overdue">Overdue</option><option value="7">Next 7 days</option><option value="30">Next 30 days</option></select></div>
      <button className="b" disabled={!shown.length} onClick={() => exportCsv(shown, ready)}>Export CSV</button>
    </div>
    <p className="mute" style={{ margin: '0 0 8px' }}>Showing {shown.length} of {rows.length}</p>
    <Table head={['Tender', 'Authority / issuer', 'Value', 'Status', 'Deadline', 'Ready', '']} empty={rows.length ? 'No tenders match these filters.' : 'Nothing here yet.'} rows={shown.map((c) => [
      <><button className="b" onClick={() => { setOpen(open === c.id ? null : c.id); setEdit({ ...c.data }) }}>{open === c.id ? 'Hide' : 'Details'}</button> {c.title}</>,
      <span className="mute">{c.data.authority || c.data.issuer || '—'}</span>,
      c.data.estimatedValue ? money(num(c.data.estimatedValue), c.data.currency) : '—',
      <span className={'pill ' + (c.open ? '' : 'ok')}>{c.statusLabel}</span>,
      <>{c.deadline || '—'}{c.open && c.deadline && c.deadline < today && <span className="pill high">Overdue</span>}{c.dueSoon && c.deadline >= today && <span className="pill warn">Due soon</span>}</>,
      <span className="mute">{items.length ? ready(c) : '—'}</span>,
      <div className="acts">{c.actions.map((a) => <button key={a.to} className="b" disabled={a.blocked} title={a.blocked ? 'A second person must approve' : ''} onClick={() => go(c.id, a.to)}>{a.label}</button>)}</div>,
    ])} />
    {open && rows.find((c) => c.id === open) && <div className="form" style={{ marginTop: 16 }}>
      {def.fields.map((fd) => <div key={fd.key} style={fd.type === 'checklist' ? { gridColumn: '1 / -1' } : undefined}><label>{fd.label}</label>{canEdit ? <FieldInput fd={fd} value={edit[fd.key]} onChange={(v) => setEdit({ ...edit, [fd.key]: v })} /> : <div className="mute">{Array.isArray(edit[fd.key]) ? edit[fd.key].join(', ') || '—' : edit[fd.key] || '—'}</div>}</div>)}
      {canEdit && <div><button className="b p" onClick={() => save(open)}>Save changes</button></div>}
      {type.key === 'tender' && <div style={{ gridColumn: '1 / -1' }}><Deposits api={api} caseId={open} canEdit={canEdit} notify={notify} /></div>}
      <div style={{ gridColumn: '1 / -1' }}><Documents api={api} token={token} caseId={open} canEdit={canEdit} notify={notify} items={(def.fields.find((x) => x.type === 'checklist') || {}).options || []} /></div>
    </div>}
  </>)
}


// ---- dashboard / list helpers (computed in the browser from the case list) ----
const num = (v) => { const n = Number(v); return Number.isFinite(n) ? n : 0 }
const money = (v, cur) => {
  const c = cur || 'INR'
  if (c === 'INR') return v >= 1e7 ? 'INR ' + (v / 1e7).toFixed(2) + ' Cr' : v >= 1e5 ? 'INR ' + (v / 1e5).toFixed(1) + ' L' : 'INR ' + Math.round(v).toLocaleString('en-IN')
  return c + ' ' + new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(v)
}
const totals = (list) => {
  const t = {}
  list.forEach((c) => { const cur = (c.data && c.data.currency) || 'INR'; t[cur] = (t[cur] || 0) + num(c.data && c.data.estimatedValue) })
  return Object.entries(t).filter(([, v]) => v > 0).map(([cur, v]) => money(v, cur)).join(' + ') || '—'
}
const daysFromToday = (iso) => Math.round((new Date(iso + 'T00:00:00') - new Date(new Date().toDateString())) / 86400000)
const byDeadline = (a, b) => (b.open - a.open) || (a.deadline || '9999').localeCompare(b.deadline || '9999')
const dueText = (iso) => { const n = daysFromToday(iso); return n < 0 ? `${-n} day${n === -1 ? '' : 's'} overdue` : n === 0 ? 'today' : `in ${n} day${n === 1 ? '' : 's'}` }

function exportCsv(list, ready) {
  const head = ['Title', 'Authority', 'Stage', 'Submission deadline', 'Estimated value', 'Currency', 'Deposit', 'Pre-bid meeting', 'Clarification deadline', 'Checklist ready', 'Created by']
  const safe = (v) => { const t = String(v ?? ''); return /^[=+\-@\t\r]/.test(t) && Number.isNaN(Number(t)) ? "'" + t : t } // stops spreadsheet formula injection
  const cell = (v) => '"' + safe(v).replace(/"/g, '""') + '"'
  const lines = [head, ...list.map((c) => [c.title, c.data.authority, c.statusLabel, c.deadline, c.data.estimatedValue, c.data.currency, c.data.depositAmount, c.data.preBidMeeting, c.data.clarificationDeadline, ready(c), c.createdBy])]
  const blob = new Blob(['\uFEFF' + lines.map((l) => l.map(cell).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob), a = document.createElement('a')
  a.href = url; a.download = `tenders-${new Date().toISOString().slice(0, 10)}.csv`; a.click(); URL.revokeObjectURL(url)
}

const AVAILABLE = [{ key: 'tender', name: 'Tender tracking' }, { key: 'library', name: 'Company document library' }]
const LEVEL = { urgent: 'high', warn: 'warn', info: '' }
const DEPOSIT_KINDS = { EARNEST_MONEY: 'Earnest money', TENDER_FEE: 'Tender fee', BID_GUARANTEE: 'Bid guarantee', PERFORMANCE_GUARANTEE: 'Performance guarantee', OTHER: 'Other' }
const INSTRUMENTS = { BANK_GUARANTEE: 'Bank guarantee', DEMAND_DRAFT: 'Demand draft', ONLINE: 'Online payment', CHEQUE: 'Cheque', CASH: 'Cash', OTHER: 'Other' }
const sumDeposits = (list) => { const t = {}; list.forEach((d) => { t[d.currency] = (t[d.currency] || 0) + Number(d.amount) }); return Object.entries(t).map(([c, v]) => money(v, c)).join(' + ') || '—' }
const lockedText = (dep) => Object.entries(dep.lockedByCurrency || {}).map(([c, v]) => money(Number(v), c)).join(' + ') || '—'

const errText = async (r) => { try { return (await r.json()).message || 'Request failed' } catch { return 'Request failed (' + r.status + ')' } }
const kb = (n) => (n < 1048576 ? Math.max(1, Math.round(n / 1024)) + ' KB' : (n / 1048576).toFixed(1) + ' MB')

function Documents({ api, token, caseId, canEdit, notify, items }) {
  const [docs, load] = useLoad(api, `/api/documents?caseId=${caseId}`, notify)
  const [file, setFile] = useState(null), [item, setItem] = useState(''), [k, setK] = useState(0)
  const upload = async () => {
    if (!file) return notify('Choose a file first')
    const fd = new FormData(); fd.append('caseId', caseId); if (item) fd.append('checklistItem', item); fd.append('file', file)
    try {
      const r = await fetch(GW + '/api/documents', { method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: fd })
      if (!r.ok) throw new Error(await errText(r))
      notify('Uploaded'); setFile(null); setItem(''); setK(k + 1); load()
    } catch (e) { notify(e.message) }
  }
  const download = async (d) => {
    try {
      const r = await fetch(`${GW}/api/documents/${d.id}/download`, { headers: { Authorization: 'Bearer ' + token } })
      if (!r.ok) throw new Error(await errText(r))
      const url = URL.createObjectURL(await r.blob()), a = document.createElement('a')
      a.href = url; a.download = d.fileName; a.click(); URL.revokeObjectURL(url)
    } catch (e) { notify(e.message) }
  }
  const remove = (d) => api('DELETE', `/api/documents/${d.id}`).then(() => { notify('Deleted'); load() }).catch((e) => notify(e.message))
  return (<>
    <h2>Documents</h2>
    {canEdit && <div className="add" style={{ gridTemplateColumns: '2fr 2fr auto', marginBottom: 12 }}>
      <div><label htmlFor="df">File (PDF, Word, Excel, image, text, up to 20 MB)</label><input key={k} id="df" type="file" onChange={(e) => setFile(e.target.files[0] || null)} /></div>
      <div><label htmlFor="di">For checklist item (optional)</label><select id="di" value={item} onChange={(e) => setItem(e.target.value)}><option value="">General</option>{items.map((o) => <option key={o}>{o}</option>)}</select></div>
      <button className="b p" onClick={upload}>Upload</button>
    </div>}
    <Table head={['File', 'For', 'Size', 'Uploaded by', '']} empty="No documents yet." rows={docs.map((d) => [
      d.fileName, <span className="mute">{d.checklistItem || 'General'}</span>, kb(d.sizeBytes),
      <span className="mute">{d.uploadedBy} · {new Date(d.uploadedAt).toLocaleDateString()}</span>,
      <div className="acts"><button className="b" onClick={() => download(d)}>Download</button>{canEdit && <button className="b" onClick={() => remove(d)}>Delete</button>}</div>,
    ])} />
  </>)
}

function Dashboard({ api, notify, type, goTo }) {
  const def = type.definition
  const [rows] = useLoad(api, `/api/flow/cases?type=${type.key}`, notify)
  const [dep, setDep] = useState(null)
  useEffect(() => { api('GET', '/api/flow/deposits/summary').then(setDep).catch(() => {}) }, [api])
  const items = (def.fields.find((x) => x.type === 'checklist') || {}).options || []
  const ready = (c) => (c.data.checklist || []).length + '/' + items.length
  const open = rows.filter((c) => c.open)
  const left = (c) => (c.deadline ? daysFromToday(c.deadline) : null)
  const overdue = open.filter((c) => left(c) !== null && left(c) < 0)
  const soon = open.filter((c) => left(c) !== null && left(c) >= 0 && left(c) <= 7)
  const won = rows.filter((c) => c.status === 'WON'), lost = rows.filter((c) => c.status === 'LOST')
  const rate = won.length + lost.length ? Math.round((100 * won.length) / (won.length + lost.length)) + '%' : '—'
  const stages = def.states.map((s) => ({ ...s, n: rows.filter((c) => c.status === s.key).length }))
  const max = Math.max(1, ...stages.map((s) => s.n))
  const attention = [...overdue, ...soon].sort(byDeadline)
  const labels = { preBidMeeting: 'Pre-bid meeting', clarificationDeadline: 'Clarification deadline', submissionDeadline: 'Submission deadline' }
  const events = []
  open.forEach((c) => Object.keys(labels).forEach((k) => { const v = c.data[k]; if (v) { const n = daysFromToday(v); if (n >= 0 && n <= 30) events.push({ date: v, n, label: labels[k], c }) } }))
  events.sort((a, b) => a.date.localeCompare(b.date))
  return (<>
    <h1>Dashboard</h1>
    <p className="sub">Where your bids stand today.</p>
    {rows.length === 0 ? <p className="mute">No tenders yet. Add the first one under {type.name}.</p> : <>
      <div className="kpis">
        <div className="kpi"><b>{open.length}</b><span>Active tenders</span></div>
        <div className={'kpi' + (overdue.length ? ' bad' : '')}><b>{overdue.length}</b><span>Overdue</span></div>
        <div className="kpi"><b>{soon.length}</b><span>Due in 7 days</span></div>
        <div className="kpi"><b>{totals(open)}</b><span>Pipeline value</span></div>
        <div className="kpi"><b>{totals(won)}</b><span>Won value</span></div>
        <div className="kpi"><b>{rate}</b><span>Win rate</span></div>
        <div className="kpi"><b>{dep ? lockedText(dep) : '—'}</b><span>Deposits locked up</span></div>
        <div className={'kpi' + (dep && dep.refundsOverdue ? ' bad' : '')}><b>{dep ? dep.refundsOverdue + dep.refundsDueIn7Days : '—'}</b><span>Refunds due or overdue</span></div>
      </div>
      <h2>Tenders by stage</h2>
      <div className="bars">{stages.map((s) => <div className="bar" key={s.key}><span>{s.label}</span><div className="track"><i style={{ width: (s.n / max) * 100 + '%' }} /></div><span className="mute">{s.n}</span></div>)}</div>
      <div className="grid2">
        <div><h2>Needs attention</h2>
          <Table head={['Tender', 'Deadline', 'Ready', 'Stage']} empty="Nothing overdue or due this week." rows={attention.slice(0, 8).map((c) => [
            <>{c.title}<div className="mute">{c.data.authority}</div></>,
            <span className={left(c) < 0 ? 'od' : ''}>{c.deadline}<div className="mute">{dueText(c.deadline)}</div></span>,
            <span className="mute">{ready(c)}</span>, c.statusLabel])} /></div>
        <div><h2>Next 30 days</h2>
          <Table head={['Date', 'What', 'Tender']} empty="No dates in the next 30 days." rows={events.slice(0, 12).map((e) => [
            <>{e.date}<div className="mute">{dueText(e.date)}</div></>, e.label, <>{e.c.title}<div className="mute">{e.c.data.authority}</div></>])} /></div>
      </div>
      <p style={{ marginTop: 16 }}><button className="b" onClick={() => goTo('flow:' + type.key)}>Open all tenders</button></p>
    </>}
  </>)
}

function Users({ api, notify, myName }) {
  const [rows, load] = useLoad(api, '/api/users', notify)
  const blank = { username: '', email: '', displayName: '', role: 'coordinator', password: '' }
  const [f, setF] = useState(blank), [reset, setReset] = useState(null), [pw, setPw] = useState('')
  const create = () => {
    if (!f.username.trim() || !f.email.trim() || !f.password) return notify('Username, email and an initial password are required')
    api('POST', '/api/users', f).then(() => { notify('User created. They choose their own password at first sign-in.'); setF(blank); load() }).catch((e) => notify(e.message))
  }
  const patch = (u, body) => api('PUT', `/api/users/${u.id}`, body).then(() => { notify('Saved'); load() }).catch((e) => { notify(e.message); load() })
  const doReset = () => {
    if (!pw) return notify('Enter a temporary password')
    api('POST', `/api/users/${reset.id}/reset-password`, { newPassword: pw }).then(() => { notify('Password reset. They must choose a new one at next sign-in.'); setReset(null); setPw('') }).catch((e) => notify(e.message))
  }
  return (<>
    <h1>Users</h1>
    <p className="sub">Everyone here belongs to your organization. Admins do everything, including this page and Settings. Coordinators create and edit tenders and documents. Viewers can only look.</p>
    <h2>Add a user</h2>
    <div className="form">
      <div><label htmlFor="nu">Username</label><input id="nu" value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} placeholder="lowercase, no spaces" /></div>
      <div><label htmlFor="ne">Email</label><input id="ne" type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></div>
      <div><label htmlFor="nn">Name (optional)</label><input id="nn" value={f.displayName} onChange={(e) => setF({ ...f, displayName: e.target.value })} /></div>
      <div><label htmlFor="nr">Role</label><select id="nr" value={f.role} onChange={(e) => setF({ ...f, role: e.target.value })}><option value="admin">Admin</option><option value="coordinator">Coordinator</option><option value="viewer">Viewer</option></select></div>
      <div><label htmlFor="np">Initial password</label><input id="np" type="text" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} placeholder="8+ characters, a letter and a number" /></div>
      <div><button className="b p" onClick={create}>Add user</button></div>
    </div>
    {reset && <div className="form" style={{ borderColor: 'var(--brand)' }}>
      <div><label htmlFor="rp">Temporary password for {reset.username}</label><input id="rp" type="text" value={pw} onChange={(e) => setPw(e.target.value)} /></div>
      <div><button className="b p" onClick={doReset}>Reset password</button> <button className="b" onClick={() => { setReset(null); setPw('') }}>Cancel</button></div>
    </div>}
    <Table head={['User', 'Email', 'Role', 'Status', 'Last sign-in', '']} empty="No users yet." rows={rows.map((u) => [
      <>{u.username}{u.displayName && <div className="mute">{u.displayName}</div>}</>,
      <span className="mute">{u.email}</span>,
      <select aria-label={'Role for ' + u.username} value={u.role} onChange={(e) => patch(u, { role: e.target.value })}><option value="admin">Admin</option><option value="coordinator">Coordinator</option><option value="viewer">Viewer</option></select>,
      <><span className={'pill ' + (u.enabled ? 'ok' : 'high')}>{u.enabled ? 'Active' : 'Disabled'}</span>{u.mustChangePassword && <span className="pill warn">New password needed</span>}</>,
      <span className="mute">{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : 'Never'}</span>,
      <div className="acts"><button className="b" onClick={() => { setReset(u); setPw('') }}>Reset password</button><button className="b" disabled={u.username === myName} title={u.username === myName ? 'You cannot disable yourself' : ''} onClick={() => patch(u, { enabled: !u.enabled })}>{u.enabled ? 'Disable' : 'Enable'}</button></div>,
    ])} />
    <p className="mute small">Role and status changes apply at the person’s next sign-in. Logins last about 30 minutes.</p>
  </>)
}

function Account({ api, notify }) {
  const [me] = useLoad(api, '/api/me', notify)
  const u = Array.isArray(me) ? null : me
  return (<>
    <h1>My account</h1>
    {u && <p className="sub">Signed in as <b>{u.username}</b> ({u.role}){u.email ? ' · ' + u.email : ''}</p>}
    <h2>Change password</h2>
    <PasswordForm api={api} notify={notify} />
  </>)
}

function Reminders({ api, notify, goTo, onChange }) {
  const [rows, load] = useLoad(api, '/api/flow/reminders', notify)
  useEffect(() => { api('POST', '/api/flow/reminders/refresh').then(() => { load(); onChange() }).catch(() => {}) }, [api])
  const mark = (id) => api('POST', `/api/flow/reminders/${id}/read`).then(() => { load(); onChange() }).catch((e) => notify(e.message))
  const markAll = () => api('POST', '/api/flow/reminders/read-all').then(() => { load(); onChange() }).catch((e) => notify(e.message))
  return (<>
    <h1>Reminders</h1>
    <p className="sub">Deadlines, deposit refunds and document expiries that are getting close. Everyone in your organization sees the same list.</p>
    <p><button className="b" disabled={!rows.some((r) => !r.read)} onClick={markAll}>Mark all as read</button></p>
    <Table head={['', 'Reminder', 'Date', '']} empty="Nothing needs attention right now." rows={rows.map((r) => [
      <span className={'pill ' + (r.read ? '' : LEVEL[r.level])}>{r.read ? 'Read' : r.level === 'urgent' ? 'Urgent' : r.level === 'warn' ? 'Soon' : 'Heads-up'}</span>,
      <span style={r.read ? { opacity: 0.6 } : undefined}>{r.message}</span>,
      <span className="mute">{r.dueOn}</span>,
      <div className="acts">{r.typeKey && <button className="b" onClick={() => goTo('flow:' + r.typeKey)}>Open</button>}{!r.read && <button className="b" onClick={() => mark(r.id)}>Mark read</button>}</div>,
    ])} />
  </>)
}

function Deposits({ api, caseId, canEdit, notify }) {
  const [rows, load] = useLoad(api, `/api/flow/deposits?caseId=${caseId}`, notify)
  const blank = { kind: 'EARNEST_MONEY', amount: '', currency: 'INR', instrument: 'DEMAND_DRAFT', reference: '', paidOn: '', validUntil: '', refundDueOn: '' }
  const [f, setF] = useState(blank)
  const nul = (v) => v || null
  const add = () => {
    if (!(Number(f.amount) > 0)) return notify('Enter the amount')
    api('POST', '/api/flow/deposits', { caseId, kind: f.kind, amount: Number(f.amount), currency: f.currency, instrument: f.instrument, reference: f.reference, paidOn: nul(f.paidOn), validUntil: nul(f.validUntil), refundDueOn: nul(f.refundDueOn) })
      .then(() => { notify('Deposit added'); setF(blank); load() }).catch((e) => notify(e.message))
  }
  const change = (d, body, msg) => api('PUT', `/api/flow/deposits/${d.id}`, body).then(() => { notify(msg); load() }).catch((e) => notify(e.message))
  const remove = (d) => api('DELETE', `/api/flow/deposits/${d.id}`).then(() => { notify('Deleted'); load() }).catch((e) => notify(e.message))
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })
  const today = new Date().toISOString().slice(0, 10)
  const locked = rows.filter((d) => d.status === 'PAID')
  const label = { PLANNED: 'Planned', PAID: 'Paid', REFUNDED: 'Refunded', FORFEITED: 'Forfeited' }
  const tone = { PLANNED: '', PAID: 'warn', REFUNDED: 'ok', FORFEITED: 'high' }
  return (<>
    <h2>Deposits and guarantees</h2>
    <p className="mute small">Money tied up in this bid. Mark it paid, then refunded when it comes back. You get reminders before a refund is due or a guarantee expires.</p>
    {canEdit && <div className="form" style={{ marginBottom: 12 }}>
      <div><label>Type</label><select value={f.kind} onChange={set('kind')}>{Object.entries(DEPOSIT_KINDS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
      <div><label>Amount</label><input type="number" value={f.amount} onChange={set('amount')} /></div>
      <div><label>Currency</label><input value={f.currency} maxLength={3} onChange={(e) => setF({ ...f, currency: e.target.value.toUpperCase() })} /></div>
      <div><label>How</label><select value={f.instrument} onChange={set('instrument')}>{Object.entries(INSTRUMENTS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
      <div><label>Reference number</label><input value={f.reference} onChange={set('reference')} /></div>
      <div><label>Paid on (if paid)</label><input type="date" value={f.paidOn} onChange={set('paidOn')} /></div>
      <div><label>Valid until</label><input type="date" value={f.validUntil} onChange={set('validUntil')} /></div>
      <div><label>Refund due on</label><input type="date" value={f.refundDueOn} onChange={set('refundDueOn')} /></div>
      <div><button className="b p" onClick={add}>Add</button></div>
    </div>}
    <Table head={['Type', 'Amount', 'How', 'Valid until', 'Refund due', 'Status', '']} empty="No deposits recorded for this tender." rows={rows.map((d) => [
      <>{DEPOSIT_KINDS[d.kind] || d.kind}{d.reference && <div className="mute">{d.reference}</div>}</>,
      money(Number(d.amount), d.currency), <span className="mute">{INSTRUMENTS[d.instrument] || '—'}</span>, d.validUntil || '—',
      <>{d.refundDueOn || '—'}{d.refundOverdue && <span className="pill high">Overdue</span>}</>,
      <span className={'pill ' + tone[d.status]}>{label[d.status] || d.status}</span>,
      <div className="acts">{canEdit && d.status === 'PLANNED' && <button className="b" onClick={() => change(d, { status: 'PAID', paidOn: today }, 'Marked paid')}>Mark paid</button>}
        {canEdit && d.status === 'PAID' && <><button className="b" onClick={() => change(d, { status: 'REFUNDED', refundedOn: today }, 'Marked refunded')}>Refunded</button><button className="b" onClick={() => change(d, { status: 'FORFEITED' }, 'Marked forfeited')}>Forfeited</button></>}
        {canEdit && <button className="b" onClick={() => remove(d)}>Delete</button>}</div>,
    ])} />
    {locked.length > 0 && <p className="mute small">Locked up in this tender: <b>{sumDeposits(locked)}</b></p>}
  </>)
}

function Table({ head, rows, empty }) {
  return (<div className="tablewrap"><table><thead><tr>{head.map((h, i) => <th key={i}>{h}</th>)}</tr></thead>
    <tbody>{rows.length ? rows.map((r, i) => <tr key={i}>{r.map((c, j) => <td key={j}>{c}</td>)}</tr>) : <tr><td colSpan={head.length} className="mute">{empty}</td></tr>}</tbody></table></div>)
}
