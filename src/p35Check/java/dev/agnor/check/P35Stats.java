package dev.agnor.check;
import com.google.gson.*;
import java.util.*;
final class P35Stats {
    static JsonObject nanos(List<Long> values){var result=new JsonObject();result.addProperty("count",values.size());if(!values.isEmpty()){var sorted=values.stream().sorted().toList();result.addProperty("meanMs",values.stream().mapToDouble(Long::doubleValue).average().orElse(0)/1e6);result.addProperty("p95Ms",sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(sorted.size()*.95)-1))/1e6);}return result;}
}
