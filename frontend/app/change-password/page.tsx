"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { fetchCurrentUser, type LoginResponse } from "@/lib/auth";
import { post } from "@/lib/api";

export default function ChangePasswordPage() {
  const router = useRouter();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    fetchCurrentUser().then(user => {
      if (!active) return;
      if (!user) router.replace("/login");
      else if (!user.passwordChangeRequired) router.replace("/dashboard");
    }).catch(() => router.replace("/login"));
    return () => { active = false; };
  }, [router]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    if (newPassword !== confirmation) return setError("The new passwords do not match.");
    setSaving(true);
    try {
      await post<LoginResponse>("/auth/change-password", { currentPassword, newPassword });
      router.replace("/dashboard");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Password could not be changed.");
    } finally { setSaving(false); }
  }

  return <main className="flex min-h-screen items-center justify-center px-6 py-12"><section className="auth-panel">
    <p className="eyebrow">Account security</p><h1 className="mt-3 text-3xl font-bold">Choose your password</h1>
    <p className="mt-2 text-sm text-slate-600">Your administrator provided a one-time password. Set a private password to continue.</p>
    <form className="mt-8 space-y-5" onSubmit={submit}>
      <label className="block"><span className="mb-2 block text-sm font-medium text-slate-700">One-time password</span><input className="field px-4 py-3" autoComplete="current-password" required type="password" value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} /></label>
      <label className="block"><span className="mb-2 block text-sm font-medium text-slate-700">New password</span><input className="field px-4 py-3" autoComplete="new-password" minLength={12} required type="password" value={newPassword} onChange={event => setNewPassword(event.target.value)} /><small className="mt-1 block text-slate-500">Use at least 12 characters.</small></label>
      <label className="block"><span className="mb-2 block text-sm font-medium text-slate-700">Confirm new password</span><input className="field px-4 py-3" autoComplete="new-password" minLength={12} required type="password" value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label>
      {error ? <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div> : null}
      <button className="btn-primary w-full py-3" disabled={saving} type="submit">{saving ? "Saving password…" : "Save password and continue"}</button>
    </form>
  </section></main>;
}
