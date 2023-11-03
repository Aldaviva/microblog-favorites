
package org.slf4j.impl;

/**
 * This class must exist for Twitter4J to use SLF4J logging, even if it is empty.
 * Note that this project already depends on SLF4J and Logback, but it does not include the real copy of this class because this is a Log4j class, which this project does not use.
 * This class is not used other than Twitter4J checking for its existence, so it doesn't implement any interfaces or methods.
 * That's right, Yusuke Yamamoto (the Twitter4J maintainer) is too retarded to know the difference between SLF4J and Log4j.
 */
public final class StaticLoggerBinder {

}