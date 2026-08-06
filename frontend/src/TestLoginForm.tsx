import { useState, type FormEvent } from 'react'
import { testLogin } from './api'

/**
 * 테스트 ID/PW 로그인 폼(카드사 심사·데모용). 구글 로그인과 별개로, 지정된 테스트 계정으로 접속.
 * 성공 시 세션 쿠키가 설정되며, onSuccess로 로그인 상태를 갱신한다.
 */
export default function TestLoginForm({ onSuccess }: { onSuccess: () => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!username.trim() || !password) return
    setBusy(true)
    setError(null)
    try {
      await testLogin(username.trim(), password)
      onSuccess()
    } catch (err) {
      setError(String((err as Error).message ?? err))
      setBusy(false)
    }
  }

  return (
    <form className="test-login" onSubmit={submit}>
      <div className="test-login-title">테스트 계정으로 로그인</div>
      <input
        className="text"
        placeholder="아이디"
        value={username}
        autoComplete="username"
        onChange={(e) => setUsername(e.target.value)}
      />
      <input
        className="text"
        type="password"
        placeholder="비밀번호"
        value={password}
        autoComplete="current-password"
        onChange={(e) => setPassword(e.target.value)}
      />
      {error && <p className="error-text center small">{error}</p>}
      <button className="btn primary" type="submit" disabled={busy || !username.trim() || !password}>
        {busy ? '로그인 중…' : '로그인'}
      </button>
    </form>
  )
}
