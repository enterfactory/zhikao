package com.zhikao.modules.workflow;
import com.zhikao.entity.*;
import com.zhikao.mapper.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import java.time.LocalDateTime;
import java.util.*;
@TestComponent
public class EcsTestSupport {
 public static final String PASSWORD="StageAReal2026!";
 @Autowired SysUserMapper users;
 @Autowired SysClassMapper classes;
 @Autowired SubjectMapper subjects;
 @Autowired SysUserRoleMapper links;
 @Autowired SysRoleMapper roles;
 public static String schema(String test){return "zhikao_test_"+test+"_"+UUID.randomUUID().toString().replace("-","").substring(0,12);}
 public static void configure(DynamicPropertyRegistry r,String schema){
  r.add("ZHIKAO_BOOTSTRAP_PASSWORD",()->"BootstrapTest2026");
  r.add("spring.datasource.url",()->"jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT:5432}/${POSTGRES_DB}?currentSchema="+schema+"&connectTimeout=5&socketTimeout=30");
  r.add("spring.flyway.default-schema",()->schema);
  r.add("spring.flyway.schemas",()->schema);
  r.add("zhikao.redis.namespace",()->schema+":");
  r.add("zhikao.vector.collection",()->schema);
  r.add("zhikao.demo-data.enabled",()->false);
  r.add("zhikao.crawler.recovery-enabled",()->false);
  r.add("zhikao.export.recovery-enabled",()->false);
  r.add("zhikao.exam.timeout-scan-initial-delay-ms",()->3600000);
  r.add("zhikao.upload.dir",()->"./target/"+schema+"/uploads");
  r.add("zhikao.crawler.ingest-dir",()->"./target/"+schema+"/ingest");
 }
 public static void cleanupRedis(org.springframework.data.redis.connection.RedisConnectionFactory factory,String schema){
  if(!schema.matches("zhikao_test_[a-z]+_[0-9a-f]{12}"))throw new IllegalArgumentException("Not a test namespace");
  try(var connection=factory.getConnection();var cursor=connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions().match(schema+":*").count(200).build())){
   var keys=new ArrayList<byte[]>();
   while(cursor.hasNext())keys.add(cursor.next());
   for(int i=0;i<keys.size();i+=100)connection.del(keys.subList(i,Math.min(i+100,keys.size())).toArray(byte[][]::new));
  }
 }
 public void seed(){
  if(classes.selectCount(null)==0)classes.insert(new SysClass(null,"Stage A 验证班","2026","验证",LocalDateTime.now()));
  if(subjects.selectCount(null)==0)subjects.insert(new Subject(null,"数学","STAGE_A","test",LocalDateTime.now()));
  Long classId=classes.selectList(null).get(0).getId();
  for(String name:List.of("admin","teacher","student","student02")){
   if(users.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,name))>0)continue;
   int type=name.equals("admin")?1:name.equals("teacher")?2:3;
   var user=SysUser.builder().username(name).realName("Stage A "+name).userType(type).status(1)
    .classId(type==3?classId:null).password(org.mindrot.jbcrypt.BCrypt.hashpw(PASSWORD,org.mindrot.jbcrypt.BCrypt.gensalt()))
    .passwordChangeRequired(0).createTime(LocalDateTime.now()).build();
   users.insert(user);
   String code=type==1?"super_admin":type==2?"teacher":"student";
   var role=roles.selectOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode,code));
   links.insert(SysUserRole.builder().userId(user.getId()).roleId(role.getId()).build());
  }
 }
}
