export function GET() {
  const openaiConfigured = Boolean(process.env.OPENAI_API_KEY);
  const liyaConfigured = Boolean(process.env.OPENAI_API_KEY && process.env.LIYA_DEVICE_TOKEN);

  return Response.json({
    ok: true,
    service: 'vianer-assistant-backend',
    checkedAt: new Date().toISOString(),
    services: {
      backend: 'connected',
      openai: openaiConfigured ? 'connected' : 'waiting',
      liya: liyaConfigured ? 'connected' : 'waiting',
    },
  });
}
