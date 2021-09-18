package myhibernate;

import myhibernate.ann.*;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MyHibernate
{

	// Recibe el class del Mapping sobre el que queremos
	// buscar la fila identificada por id
	public static <T> T find(Class<T> clazz,int id)
	{

		// 1. generar el SQL dinamico
		// 		1.1. Sin JOIN (o relacion) => 1 semana
		// 		1.2. Considerando relaciones ManyToOne
		// API: Java Reflection (o introspeccion)

		Optional<Field> opColumnaId = Arrays.stream(clazz.getDeclaredFields()).filter(fld -> fld.getAnnotation(Id.class) != null).findFirst();
		String columnaId;
		if(opColumnaId.isPresent()){ // Esto no es necesario (Solo esta para que no salte Warning)
			columnaId = opColumnaId.get().getAnnotation(Column.class).name();
		} else {
			throw new RuntimeException("No se ha encontrado una columna con Annotation: @ID");
		}
		String query = generarSQLdinamico(clazz,columnaId + " = " + id);

		// 2. Invocar o ejecutar el SQL generado en (1)
		// para obtener el ResultSet
		// API: JDBC (acceso a DB desde Java)

		ResultSet rs = ejecutarQuerySQL(query);

		// 3. Leer los datos obtenidos en (2), instanciar
		// el objeto y retornarlo.

		return obtenerObjetos(rs,clazz).get(0);
	}

	public static <T> List<T> findAll(Class<T> clazz)
	{
		return obtenerObjetos(ejecutarQuerySQL(generarSQLdinamico(clazz)),clazz);
	}

	public static Query createQuery(String hql)
	{
		// PROGRAMAR AQUI
		return null;
	}

	// Itera el ResultSet para instanciar los objetos
	private static <T> List<T> obtenerObjetos(ResultSet rs, Class<T> clazz){
		List<T> retorno = new ArrayList<>();
		try{
			while(rs.next()){
				retorno.add(instanciarObjeto(rs,clazz));
			}
		}catch(Exception e){
			e.printStackTrace();
			throw new RuntimeException("Error al obtener resultado del ResultSet");
		}
		return retorno;
	}
	// Genera la instancia de un objeto según los datos del ResultSet
	private static <T> T instanciarObjeto(ResultSet rs, Class<T> clazz){
		Field[] fields = clazz.getDeclaredFields();
		for(Field field: fields) {
			field.setAccessible(true);
		}

		T result;
		try {
			result = clazz.newInstance();
			Object dato;
			for(Field field : fields){
				if(field.isAnnotationPresent(Column.class)) {
					dato = rs.getObject(field.getAnnotation(Column.class).name(), field.getType());
					field.set(result,dato);
				}
				if(field.isAnnotationPresent(JoinColumn.class)){
					dato = instanciarObjeto(rs,field.getType());
					field.set(result,dato);
				}
			}
		} catch (Exception e){
			e.printStackTrace();
			throw new RuntimeException("Error en la instanciación de un objeto");
		}

		return result;
	}

	// Genera una query SQL de forma dinámica con una condición
	private static <T> String generarSQLdinamico(Class<T> clazz, String condicion){

		if(clazz.getAnnotation(Entity.class) == null) {
			throw new RuntimeException("La clase no tiene la Annotation @Entity");
		}

		String nombreTablaPrincipal = clazz.getAnnotation(Table.class).name();

		String columnas = darFormatoAColumnas(clazz);
		columnas = columnas.substring(0, columnas.length() - 1); //Solo para sacarle el último ","

		String joins = darFormatoAJoin(clazz);
		if(!condicion.equals(""))
		if(joins.equals("")){
			joins = joins.concat(" WHERE ");
		} else {
			joins = joins.concat(" AND ");
		}

		return "SELECT " + columnas + " FROM " + nombreTablaPrincipal + joins + condicion;
	}

	// Genera una query SQL de forma dinámica
	private static <T> String generarSQLdinamico(Class<T> clazz){
		return generarSQLdinamico(clazz, "");
	}

	// Establece la conexión con la base de datos y ejecuta la query SQL
	private static ResultSet ejecutarQuerySQL(String query){
		try {
			Class.forName("org.hsqldb.jdbc.JDBCDriver").newInstance();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Driver de HSQLDB no encontrado");
		}
		try {
			Connection c = DriverManager.getConnection("jdbc:hsqldb:hsql://localhost/xdb", "sa", "");
			System.out.println("+ Query ejecutado -> " + query);
			return c.createStatement().executeQuery(query);
		} catch (SQLException e) {
			e.printStackTrace();
			throw new RuntimeException("Error en la conexion con la base de datos, no se pudo ejecutar el Query");
		}
	}

	// Le da formato a las columnas que se necesitan para instanciar la clases (Para el query SQL)
	private static <T> String darFormatoAColumnas(Class<T> clazz){
		String nombreTabla = clazz.getAnnotation(Table.class).name();
		String columnas = "";

		for(Field field : clazz.getDeclaredFields()){
			if(field.isAnnotationPresent(Column.class)) {
				columnas = columnas.concat(nombreTabla + "." + field.getAnnotation(Column.class).name());
				columnas = columnas.concat(",");
				continue;
			}
			if(field.isAnnotationPresent(JoinColumn.class)) {
				columnas = columnas.concat(darFormatoAColumnas(field.getType()));
			}
		}

		return columnas;
	}

	// Le da formato a los Join en caso de que hayan (Para el query SQL)
	private static <T> String darFormatoAJoin(Class<T> clazz){
		String nombreTabla = clazz.getAnnotation(Table.class).name();
		String joins = "";
		String auxNombreTabla;
		String auxJoinId;

		for(Field field : clazz.getDeclaredFields()){
			if(field.isAnnotationPresent(JoinColumn.class)){
				auxNombreTabla = field.getType().getAnnotation(Table.class).name();
				auxJoinId = field.getAnnotation(JoinColumn.class).name();
				joins = joins.concat(" INNER JOIN " + auxNombreTabla + " ON " + auxNombreTabla + "." + auxJoinId + " = " + nombreTabla + "." + auxJoinId);
				joins = joins.concat(darFormatoAJoin(field.getType())); //Solo en caso de Joins múltiples
			}
		}

		return joins;
	}
}
