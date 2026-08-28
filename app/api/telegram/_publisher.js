const API = 'https://api.telegram.org';

export async function publishTelegramVideo({ bytes, title, description = '', destinationUrl }) {
  const token = String(process.env.TELEGRAM_BOT_TOKEN || '').trim();
  const channel = String(process.env.TELEGRAM_CHANNEL_ID || '@masterpick_georgia').trim();
  if (!token) throw new Error('telegram_not_configured');
  if (!bytes?.byteLength) throw new Error('invalid_video_size');
  if (!/^https:\/\//i.test(String(destinationUrl || ''))) throw new Error('telegram_destination_required');
  const caption = [String(title || '').trim(), String(description || '').trim()].filter(Boolean).join('\n\n').slice(0, 1024);
  const form = new FormData();
  form.append('chat_id', channel);
  form.append('caption', caption);
  form.append('supports_streaming', 'true');
  form.append('reply_markup', JSON.stringify({ inline_keyboard: [[{ text: 'Открыть товар', url: String(destinationUrl) }]] }));
  form.append('video', new Blob([bytes], { type: 'video/mp4' }), 'video.mp4');
  const response = await fetch(`${API}/bot${token}/sendVideo`, { method: 'POST', body: form });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || !result?.ok) throw new Error(`telegram_send_video_${response.status}_${String(result?.description || '').slice(0, 160)}`);
  const id = String(result.result.message_id);
  const username = channel.startsWith('@') ? channel.slice(1) : '';
  return { id, url: username ? `https://t.me/${username}/${id}` : null };
}
