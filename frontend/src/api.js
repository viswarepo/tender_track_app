// Sign-in goes straight to identity-service; everything else goes through the gateway.
// Move these to env vars (import.meta.env) for deployment.
export const AUTH = 'http://localhost:8089'
const GW = 'http://localhost:8090'

async function authPost(path, body) {
  let r
  try {
    r = await fetch(AUTH + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
  } catch {
    throw new Error('Cannot reach the sign-in service at ' + AUTH + '. Start identity-service, and open this app at http://localhost:5173.')
  }
  if (!r.ok) {
    let m = ''
    try { m = (await r.json()).message } catch { /* no body */ }
    throw new Error(m || (r.status === 401 ? 'Wrong username or password' : 'Request failed (' + r.status + ')'))
  }
  return r.json()
}

export const login = (username, password) => authPost('/auth/login', { username, password })
export const register = (details) => authPost('/auth/register', details)

export const decode = (t) => JSON.parse(atob(t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))

export function client(token, onExpired) {
  return async (method, path, body) => {
    const r = await fetch(GW + path, {
      method,
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined,
    })
    const detail = async () => { try { return (await r.clone().json()).message || '' } catch { return '' } }
    if (r.status === 401) { onExpired(); throw new Error('Session expired. Sign in again.') }
    if (r.status === 403) throw new Error((await detail()) || 'Your role cannot do this')
    if (!r.ok) throw new Error((await detail()) || (r.status === 404 ? 'Not found in your organization' : r.status === 409 ? 'That step is not allowed right now' : 'Something went wrong (' + r.status + ')'))
    return r.json()
  }
}
