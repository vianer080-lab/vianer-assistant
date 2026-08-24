const OPENAI_SPEECH_URL = 'https://api.openai.com/v1/audio/speech';

export async function POST(request) {
  const apiKey = process.env.OPENAI_API_KEY;
  const deviceToken = process.env.LIYA_DEVICE_TOKEN;
  if (!apiKey || !deviceToken) return Response.json({ error: 'Server is not configured' }, { status: 503 });
  if (request.headers.get('authorization') !== `Bearer ${deviceToken}`) {
    return Response.json({ error: 'Unauthorized' }, { status: 401 });
  }
  const body = await request.json().catch(() => null);
  const text = String(body?.text || '').trim().slice(0, 1200);
  if (!text) return Response.json({ error: 'Text is required' }, { status: 400 });

  const speech = await fetch(OPENAI_SPEECH_URL, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: 'gpt-4o-mini-tts',
      voice: 'coral',
      input: text,
      instructions: 'Говори по-русски естественным мягким женским голосом. Тон тёплый, умный и спокойный. Не торопись, но не делай длинных пауз.',
      response_format: 'mp3',
    }),
  });
  if (!speech.ok) return Response.json({ error: 'Speech generation failed' }, { status: 502 });
  return new Response(speech.body, {
    headers: {
      'Content-Type': 'audio/mpeg',
      'Cache-Control': 'private, no-store',
    },
  });
}

