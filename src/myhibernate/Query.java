package myhibernate;

import java.util.HashMap;
import java.util.List;

public class Query<T>
{
   HashMap<String,Object> parameters;
   String sqlSinParametros;
   Class<T> clazz;

   public Query(String sql, Class<T> clazz){
      this.clazz = clazz;
      this.sqlSinParametros = sql;
      this.parameters = new HashMap<>();
   }

   public void setParameter(String pName,Object pValue) {
      parameters.put(pName, pValue);
   }

   public List<T> getResultList() {
      String sql = sqlSinParametros;
      for(String pName : parameters.keySet()){
         sql = sql.replaceAll( ":" + pName,"'" + parameters.get(pName).toString() + "'");
      }
      return MyHibernate.obtenerObjetos(MyHibernate.ejecutarQuerySQL(sql),clazz);
   }
}
