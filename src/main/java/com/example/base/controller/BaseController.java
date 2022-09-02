package com.example.base.controller;

import com.example.base.annotation.MyAnno;
import com.example.base.util.RedisUtil;
import lombok.AllArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

@RequestMapping("/base")
@Controller
public class BaseController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Value("${server.port}")
    private String serverPort;

    private final ReentrantLock lock = new ReentrantLock();

    //设置一个常量字符串为锁
    private  final  String nxLock="mylock";

    @Autowired
    private Redisson redisson;


    @GetMapping("/buy_goods")
    @ResponseBody
    public String  buy_goods() throws Exception {

        //return sych_buy_goods();

        //return lock_buy_goods();

        //模拟加redis的nx锁
        //return nx_buy_goods();

        //使用redission分布式锁
        return redission_buy_goods();
    }

    private String redission_buy_goods() {

        RLock lock = redisson.getLock(nxLock);

        lock.lock(10,TimeUnit.SECONDS);
        System.out.println("hello git 1");
        System.out.println("hello git 2");
        System.out.println("hello git 3");
        System.out.println("master test");
        lock.lock();
        try {
            return buy_good();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }

        return null;
    }


    private String nx_buy_goods() throws Exception {

        //设置锁对应的唯一值，标识不同线程的不同锁
        String s = UUID.randomUUID().toString().substring(0, 8);

        //1、尝试加锁
        //2、设置锁的过期时间，防止宕机导致死锁，或者业务类卡死，导致程序死锁
        //3、设置自动续期，保证业务执行时间小于锁过期时间
        Boolean ifAbsent = stringRedisTemplate.opsForValue().setIfAbsent(nxLock, s,5000, TimeUnit.SECONDS);
        if(ifAbsent){
            try {
                return buy_good();
            }finally {

//                //情况1，删除锁，不加判断是否是当前锁
//                stringRedisTemplate.opsForValue().getOperations().delete(nxLock);

                //情况2，避免删了别人的锁，要先判断是否是当前线程的锁
                /*
                if(stringRedisTemplate.opsForValue().get(nxLock).equalsIgnoreCase(s)){
                    //安全删除锁
                    stringRedisTemplate.delete(nxLock);
                }*/

                //"if redis.call(\"get\",KEYS[1]) == ARGV[1] then return redis.call(\"del\",KEYS[1]) else  return 0 end";
                //使用lua脚本释放锁,RedisScript<T> script, List<K> keys, Object... args
                /*
                String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then\n" +
                        "    return redis.call(\"del\",KEYS[1])\n" +
                        "else\n" +
                        "    return 0\n" +
                        "end";

                stringRedisTemplate.execute(new DefaultRedisScript<>(script,String.class),Collections.singletonList(nxLock),s);
                */

                /*
                String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then\n" +
                        "    return redis.call(\"del\",KEYS[1])\n" +
                        "else\n" +
                        "    return 0\n" +
                        "end";

                Jedis jedis = RedisUtil.jedisPool();
                try {
                    Object eval = jedis.eval(script, Collections.singletonList(nxLock), Collections.singletonList(s));
                    if("1".equals(eval.toString())){
                        return "删除锁成功!";
                    }else {
                        return "删除锁失败!";
                    }
                }finally {
                    if(jedis!=null){
                        jedis.close();
                    }
                }*/

                try {
//                    String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then return redis.call(\"del\",KEYS[1]) else  return 0 end";
//                    String s1 = stringRedisTemplate.execute(new DefaultRedisScript<>(script, String.class), Arrays.asList(nxLock), 100);

                    String script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
                    Long ret = stringRedisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList(nxLock), s);

                    if(ret==0){
                        return "删除锁失败!";
                    }else {
                        return "删除锁成功!";
                    }
                }finally {


                }
            }


        }else {
            return "抢锁失败!";
        }



    }

    private String buy_good() {
        String result = stringRedisTemplate.opsForValue().get("goods:001");
        int integerNum = result == null ? 0 : Integer.parseInt(result);
        if (integerNum == 0) {
            return "商品已经售罄！！！";
        }
        //令牌对应的值
        integerNum = integerNum - 1;
        stringRedisTemplate.opsForValue().set("goods:001", String.valueOf(integerNum));
        System.out.println("当前商品剩余：" + integerNum);

        return "购买成功，当前商品剩余：" + integerNum + "当前端口为：" + serverPort;
    }

    private String lock_buy_goods() {
        lock.lock();
        try {
            String result = stringRedisTemplate.opsForValue().get("goods:001");
            int integerNum = result==null?0:Integer.parseInt(result);
            if(integerNum==0){
                return "商品已经售罄！！！";
            }
            //令牌对应的值
            integerNum =integerNum-1;
            stringRedisTemplate.opsForValue().set("goods:001",String.valueOf(integerNum));
            System.out.println("当前商品剩余："+integerNum);


            return  "购买成功，当前商品剩余："+integerNum+"当前端口为："+serverPort;
        } finally {
            lock.unlock();
        }
    }


    private String sych_buy_goods() {
        synchronized (this){
            String result = stringRedisTemplate.opsForValue().get("goods:001");
            int integerNum = result==null?0:Integer.parseInt(result);
            if(integerNum==0){
                return "商品已经售罄！！！";
            }
            //令牌对应的值
            integerNum =integerNum-1;
            stringRedisTemplate.opsForValue().set("goods:001",String.valueOf(integerNum));
            System.out.println("当前商品剩余："+integerNum);


            return  "购买成功，当前商品剩余："+integerNum+"当前端口为："+serverPort;
        }
    }



//    @MyAnno(age = 18)
//    public void eat(){
////        Hashtable;
////        HashSet;
////        ArrayList;
////        LinkedHashSet;
////        TreeSet;
////        ReentrantLock;
//        //AbstractQueuedSynchronizer
//
//    }
}
