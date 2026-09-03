import { useCallback, useEffect, useState } from 'react';
import { Alert, AppState, Linking, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

const API_URL = 'https://vianer-assistant.expo.app';

async function openConnection(url, label) {
  try {
    // Android can report false for installed apps when their URL scheme is not
    // declared in package visibility. Opening the HTTPS app link directly is reliable.
    await Linking.openURL(url);
    return true;
  } catch (error) {
    Alert.alert(label, 'Не удалось открыть подключение. Проверьте интернет и повторите.');
    return false;
  }
}

async function loadHealth(path) {
  const response = await fetch(`${API_URL}${path}?t=${Date.now()}`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

export default function Socials() {
  const [youtube, setYoutube] = useState({ loading: true, configured: false, connected: false, channel: null });
  const [instagram, setInstagram] = useState({ loading: true, configured: false, connected: false, account: null });
  const [facebook, setFacebook] = useState({ loading: true, configured: false, connected: false, page: null });
  const [pinterest, setPinterest] = useState({ loading: true, configured: false, connected: false, account: null });
  const [opening, setOpening] = useState(null);

  const refreshYoutube = useCallback(async () => {
    try {
      const data = await loadHealth('/api/youtube/health');
      setYoutube({ loading: false, ...data });
    } catch {
      setYoutube((current) => ({ ...current, loading: false, error: true }));
    }
  }, []);

  const refreshInstagram = useCallback(async () => {
    try {
      const data = await loadHealth('/api/instagram/health');
      setInstagram({ loading: false, ...data });
    } catch {
      setInstagram((current) => ({ ...current, loading: false, error: true }));
    }
  }, []);

  const refreshFacebook = useCallback(async () => {
    try {
      const data = await loadHealth('/api/facebook/health');
      setFacebook({ loading: false, ...data });
    } catch {
      setFacebook((current) => ({ ...current, loading: false, error: true }));
    }
  }, []);

  const refreshPinterest = useCallback(async () => {
    try {
      const data = await loadHealth('/api/pinterest/health');
      setPinterest({ loading: false, ...data });
    } catch {
      setPinterest((current) => ({ ...current, loading: false, error: true }));
    }
  }, []);

  useEffect(() => {
    refreshYoutube();
    refreshInstagram();
    refreshFacebook();
    refreshPinterest();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        refreshYoutube();
        refreshInstagram();
        refreshFacebook();
        refreshPinterest();
      }
    });
    return () => subscription.remove();
  }, [refreshYoutube, refreshInstagram, refreshFacebook, refreshPinterest]);

  const connect = useCallback(async (service, url, label) => {
    if (opening) return;
    setOpening(service);
    await openConnection(url, label);
    setOpening(null);
  }, [opening]);

  const networks = [
    {
      name: 'Telegram',
      status: 'Работает',
      detail: 'MasterPick Georgia · канал и бот',
      active: true,
      actionLabel: 'Открыть Telegram →',
      action: () => openConnection('https://t.me/masterpick_georgia', 'Telegram'),
    },
    {
      name: 'YouTube',
      status: youtube.loading ? 'Проверка…' : youtube.error ? 'Нет связи' : youtube.connected ? 'Подключён' : youtube.configured ? 'Готов к подключению' : 'Нужна настройка',
      detail: youtube.connected ? `Канал: ${youtube.channel?.title || 'YouTube'}` : 'Нажмите и подтвердите доступ к нужному каналу Google',
      active: youtube.connected,
      actionLabel: opening === 'youtube' ? 'Открываю…' : youtube.connected ? 'Переподключить YouTube →' : 'Подключить YouTube →',
      action: youtube.configured
        ? () => connect('youtube', `${API_URL}/api/youtube/connect`, 'YouTube')
        : () => Alert.alert('YouTube', 'Сервер подключения YouTube ещё не настроен.'),
    },
    {
      name: 'Facebook',
      status: facebook.loading ? 'Проверка…' : facebook.connected ? 'Подключён' : facebook.configured ? 'Готов к подключению' : 'Нужна настройка Meta',
      detail: facebook.connected
        ? `Страница: ${facebook.page?.name || 'Facebook'}`
        : 'Подключение рабочей страницы MasterPick Georgia через Meta OAuth',
      active: facebook.connected,
      actionLabel: facebook.connected ? 'Обновить статус →' : opening === 'facebook' ? 'Открываю…' : 'Подключить Facebook →',
      action: facebook.connected ? refreshFacebook : facebook.configured
        ? () => connect('facebook', `${API_URL}/api/facebook/connect`, 'Facebook')
        : () => Alert.alert('Facebook', 'Сначала нужно добавить ключи приложения Meta.'),
    },
    {
      name: 'Instagram',
      status: instagram.loading ? 'Проверка…' : instagram.connected ? 'Подключён' : instagram.configured ? 'Готов к подключению' : 'Нужна настройка Meta',
      detail: instagram.connected
        ? `Аккаунт: @${instagram.account?.username || 'Instagram'}`
        : 'Подключение профессионального аккаунта MasterPick Global через Meta OAuth',
      active: instagram.connected,
      actionLabel: instagram.connected ? 'Обновить статус →' : opening === 'instagram' ? 'Открываю…' : 'Подключить Instagram →',
      action: instagram.connected ? refreshInstagram : instagram.configured
        ? () => connect('instagram', `${API_URL}/api/instagram/connect`, 'Instagram')
        : () => Alert.alert('Instagram', 'Сервер Meta ещё не настроен. Нужны Instagram App ID и Secret.'),
    },
    {
      name: 'Pinterest',
      status: pinterest.loading ? 'Проверка…' : pinterest.error ? 'Нет связи' : pinterest.connected ? 'Подключён' : pinterest.configured ? 'Готов к подключению' : 'Нужна настройка Pinterest',
      detail: pinterest.connected
        ? `Аккаунт: ${pinterest.account?.username || 'Pinterest'}`
        : 'Подключение бизнес-аккаунта для автоматической публикации пинов',
      active: pinterest.connected,
      actionLabel: pinterest.connected ? 'Обновить статус →' : opening === 'pinterest' ? 'Открываю…' : 'Подключить Pinterest →',
      action: pinterest.connected ? refreshPinterest : pinterest.configured
        ? () => connect('pinterest', `${API_URL}/api/pinterest/connect`, 'Pinterest')
        : () => Alert.alert('Pinterest', 'Сначала нужно создать приложение Pinterest и добавить его ключи.'),
    },
    {
      name: 'WhatsApp Business',
      status: 'Установлен на телефоне',
      detail: 'Открывает WhatsApp Business. API-подключение будет отдельным этапом.',
      actionLabel: 'Открыть WhatsApp Business →',
      action: () => openConnection('https://wa.me/?text=MasterPick%20Georgia', 'WhatsApp Business'),
    },
  ];

  return <View style={s.screen}><StatusBar barStyle="light-content" backgroundColor="#0b1220"/><ScrollView contentContainerStyle={s.content}>
    <Text style={s.kicker}>СОЦСЕТИ</Text>
    <Text style={s.title}>Каналы</Text>
    <Text style={s.subtitle}>Нажмите нужную площадку, войдите в аккаунт и подтвердите разрешения.</Text>
    {networks.map((n) => <TouchableOpacity key={n.name} style={s.card} activeOpacity={0.7} onPress={n.action} disabled={Boolean(opening)}>
      <View style={s.row}><Text style={s.name}>{n.name}</Text><View style={[s.badge,n.active?s.good:s.ready]}><Text style={s.badgeText}>{n.status}</Text></View></View>
      <Text style={s.detail}>{n.detail}</Text>
      <Text style={s.open}>{n.actionLabel}</Text>
    </TouchableOpacity>)}
    <View style={s.plan}><Text style={s.planTitle}>Подключение каналов</Text><Text style={s.planText}>YouTube, Facebook, Instagram и Pinterest подключаются через официальные API. WhatsApp Business используется отдельно для сообщений клиентам.</Text></View>
  </ScrollView></View>;
}

const s=StyleSheet.create({
  screen:{flex:1,backgroundColor:'#0b1220'},
  content:{padding:20,paddingTop:34,paddingBottom:40},
  kicker:{color:'#38bdf8',fontWeight:'800',letterSpacing:2,fontSize:13},
  title:{color:'#fff',fontSize:30,fontWeight:'800',marginTop:7},
  subtitle:{color:'#94a3b8',fontSize:16,lineHeight:23,marginTop:8,marginBottom:24},
  card:{backgroundColor:'#111c2e',borderRadius:18,padding:18,marginBottom:13,borderWidth:1,borderColor:'#22304a'},
  row:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:10},
  name:{color:'#fff',fontSize:18,fontWeight:'800',flex:1},
  badge:{borderRadius:20,paddingHorizontal:10,paddingVertical:6,maxWidth:'48%'},
  good:{backgroundColor:'#14532d'},
  ready:{backgroundColor:'#1e3a8a'},
  badgeText:{color:'#fff',fontSize:11,fontWeight:'800',textAlign:'center'},
  detail:{color:'#94a3b8',fontSize:14,lineHeight:20,marginTop:9},
  open:{color:'#7dd3fc',fontWeight:'800',marginTop:12},
  plan:{backgroundColor:'#172554',borderRadius:18,padding:18,marginTop:8},
  planTitle:{color:'#bfdbfe',fontSize:15,fontWeight:'800'},
  planText:{color:'#dbeafe',fontSize:14,lineHeight:22,marginTop:7},
});
