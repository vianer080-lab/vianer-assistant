import { ScrollView, StyleSheet, Text, View } from 'react-native';

export default function PrivacyPolicy() {
  return <View style={s.screen}><ScrollView contentContainerStyle={s.content}>
    <Text style={s.title}>Политика конфиденциальности Master Hub</Text>
    <Text style={s.updated}>Обновлено: 24 августа 2026 года</Text>
    <Text style={s.h}>Какие данные используются</Text>
    <Text style={s.p}>Master Hub подключает выбранные пользователем социальные платформы только после явного разрешения. Приложение может получать сведения об аккаунте, канале и публикациях, необходимые для показа статуса и выполнения запрошенных публикаций.</Text>
    <Text style={s.h}>Как используются данные</Text>
    <Text style={s.p}>Данные используются исключительно для работы подключённых функций Master Hub. Мы не продаём персональные данные и не используем их для сторонней рекламы.</Text>
    <Text style={s.h}>Хранение и защита</Text>
    <Text style={s.p}>Токены доступа хранятся на защищённой серверной стороне и не включаются в мобильное приложение. Пользователь может отозвать доступ в настройках соответствующей платформы.</Text>
    <Text style={s.h}>Удаление данных</Text>
    <Text style={s.p}>Чтобы отключить аккаунт или запросить удаление связанных данных, напишите на vianer080@gmail.com.</Text>
    <Text style={s.h}>Контакты</Text><Text style={s.p}>Vianer Apps · vianer080@gmail.com</Text>
  </ScrollView></View>;
}
const s=StyleSheet.create({screen:{flex:1,backgroundColor:'#0b1220'},content:{maxWidth:760,width:'100%',alignSelf:'center',padding:28,paddingBottom:50},title:{color:'#fff',fontSize:30,fontWeight:'800'},updated:{color:'#94a3b8',marginTop:10,marginBottom:22},h:{color:'#7dd3fc',fontSize:19,fontWeight:'800',marginTop:20,marginBottom:7},p:{color:'#dbeafe',fontSize:16,lineHeight:25}});
