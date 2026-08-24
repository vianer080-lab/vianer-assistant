import { ScrollView, StyleSheet, Text, View } from 'react-native';

export default function Terms() {
  return <View style={s.screen}><ScrollView contentContainerStyle={s.content}>
    <Text style={s.title}>Условия использования Master Hub</Text>
    <Text style={s.updated}>Обновлено: 24 августа 2026 года</Text>
    <Text style={s.h}>Назначение</Text><Text style={s.p}>Master Hub помогает владельцу управлять подключёнными социальными каналами, публикациями и их статусами из единой панели.</Text>
    <Text style={s.h}>Подключение аккаунтов</Text><Text style={s.p}>Пользователь самостоятельно выбирает аккаунты и предоставляет доступ через официальные экраны авторизации платформ. Использование сервиса должно соответствовать правилам Google, Meta, Pinterest, Telegram и других подключённых платформ.</Text>
    <Text style={s.h}>Ответственность пользователя</Text><Text style={s.p}>Пользователь отвечает за публикуемые материалы, законность контента и сохранность своего устройства. Master Hub не запрашивает пароли социальных сетей.</Text>
    <Text style={s.h}>Доступность</Text><Text style={s.p}>Функции могут временно ограничиваться при изменении API или правилах сторонних платформ. Мы стараемся поддерживать стабильную и безопасную работу сервиса.</Text>
    <Text style={s.h}>Прекращение использования</Text><Text style={s.p}>Пользователь может прекратить использование Master Hub и отозвать разрешения в настройках подключённой платформы.</Text>
    <Text style={s.h}>Контакты</Text><Text style={s.p}>Vianer Apps · vianer080@gmail.com</Text>
  </ScrollView></View>;
}
const s=StyleSheet.create({screen:{flex:1,backgroundColor:'#0b1220'},content:{maxWidth:760,width:'100%',alignSelf:'center',padding:28,paddingBottom:50},title:{color:'#fff',fontSize:30,fontWeight:'800'},updated:{color:'#94a3b8',marginTop:10,marginBottom:22},h:{color:'#7dd3fc',fontSize:19,fontWeight:'800',marginTop:20,marginBottom:7},p:{color:'#dbeafe',fontSize:16,lineHeight:25}});
