import { Linking, ScrollView, StatusBar, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

export default function TelegramScreen() {
  const openChannel = () => Linking.openURL('https://t.me/masterpick_georgia');

  return (
    <View style={styles.screen}>
      <StatusBar barStyle="light-content" backgroundColor="#0b1220" />
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.kicker}>TELEGRAM</Text>
        <Text style={styles.title}>MasterPick Georgia</Text>
        <Text style={styles.subtitle}>Управление каналом и автоматическими публикациями.</Text>

        <View style={styles.card}>
          <Text style={styles.label}>Статус канала</Text>
          <View style={styles.row}><View style={styles.dot} /><Text style={styles.value}>Подключён</Text></View>
          <Text style={styles.note}>Автопостинг настроен: один партнёрский пост в день.</Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.label}>Расписание</Text>
          <Text style={styles.value}>Каждый день · 11:17 Тбилиси</Text>
          <Text style={styles.note}>Amazon — основной источник. AliExpress RU&CIS — среда и суббота.</Text>
        </View>

        <TouchableOpacity style={styles.primary} onPress={openChannel}>
          <Text style={styles.primaryText}>Открыть канал Telegram</Text>
        </TouchableOpacity>

        <View style={styles.card}>
          <Text style={styles.label}>Следующее подключение</Text>
          <Text style={styles.value}>Управление публикациями</Text>
          <Text style={styles.note}>Следующим шагом добавим просмотр очереди, ручной запуск поста и историю публикаций прямо из Master Hub.</Text>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0b1220' },
  content: { padding: 20, paddingTop: 34, paddingBottom: 40 },
  kicker: { color: '#38bdf8', fontWeight: '800', letterSpacing: 2, fontSize: 13 },
  title: { color: '#fff', fontSize: 30, fontWeight: '800', marginTop: 7 },
  subtitle: { color: '#94a3b8', fontSize: 16, lineHeight: 23, marginTop: 8, marginBottom: 24 },
  card: { backgroundColor: '#111c2e', borderRadius: 18, padding: 18, marginBottom: 14, borderWidth: 1, borderColor: '#22304a' },
  label: { color: '#8fa3bf', fontSize: 13, marginBottom: 7 },
  value: { color: '#fff', fontSize: 18, fontWeight: '700' },
  note: { color: '#94a3b8', fontSize: 14, lineHeight: 20, marginTop: 9 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  dot: { width: 11, height: 11, borderRadius: 6, backgroundColor: '#22c55e' },
  primary: { backgroundColor: '#0284c7', padding: 17, borderRadius: 16, alignItems: 'center', marginBottom: 14 },
  primaryText: { color: '#fff', fontSize: 16, fontWeight: '800' },
});
