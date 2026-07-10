export default function HomePage() {
  return (
    <main className="flex min-h-screen items-center justify-center p-8">
      <section className="w-full max-w-2xl rounded-xl border border-slate-200 bg-white p-10 shadow-sm">
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500">
          TicketFlow1
        </p>
        <h1 className="mt-4 text-4xl font-bold text-slate-900">
          Frontend scaffold is in place.
        </h1>
        <p className="mt-4 text-base leading-7 text-slate-600">
          This is the default placeholder page for the Next.js App Router setup.
        </p>
        <a
          className="mt-8 inline-flex rounded-lg bg-slate-900 px-4 py-3 text-sm font-semibold text-white transition hover:bg-slate-800"
          href="/login"
        >
          Open login page
        </a>
      </section>
    </main>
  );
}
