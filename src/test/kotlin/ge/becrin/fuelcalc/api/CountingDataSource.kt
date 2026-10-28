package ge.becrin.fuelcalc.api

import org.springframework.jdbc.datasource.DelegatingDataSource
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import javax.sql.DataSource

class CountingDataSource(
    private val delegate: DataSource,

    private val counter: SqlCounter
) : DelegatingDataSource(delegate), AutoCloseable {
    override fun getConnection(): Connection =
        connection(delegate.connection)

    override fun getConnection(
        username: String,
        password: String
    ): Connection =
        connection(delegate.getConnection(username, password))

    override fun close() {
        (delegate as? AutoCloseable)?.close()
    }

    private fun connection(target: Connection): Connection =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
            val value = invoke(target, method, args)

            when (method.name) {
                "prepareStatement" -> statement(value as PreparedStatement, args?.first() as String, PreparedStatement::class.java)
                "createStatement" -> statement(value as Statement, null, Statement::class.java)
                else -> value
            }
        } as Connection

    private fun statement(
        target: Statement,
        preparedSql: String?,
        type: Class<out Statement>
    ): Statement =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, args ->
            val value = invoke(target, method, args)

            if (method.name.startsWith("execute")) {
                (preparedSql ?: args?.firstOrNull() as? String)?.let(counter::executed)
            }

            value
        } as Statement

    private fun invoke(
        target: Any,
        method: Method,
        args: Array<out Any?>?
    ): Any? =
        try {
            method.invoke(target, *(args ?: emptyArray()))
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
}
