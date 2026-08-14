---@meta

---@class log
local log = {}

---Log a message with the warn level.
---
---* A message can be a string.
---* A message may contain C-style format specifiers `%d` or `%s`. Example:
---* A message may be a scalar data type or a table. Example:
---
---The actual output will be a line in the log, containing:
---* The current timestamp
---* A module name
---* 'E', 'W', 'I', 'V' or 'D' depending on the called function.
---* `message`.
---
---**Example:**
---
--- ```lua
--- local log = require('log')
--- log.cfg { level = 'verbose' }
--- log.warn('Warning message')
--- log.info('Tarantool version: %s', box.info.version)
--- log.error({ 500, 'Internal error' })
--- log.debug('Debug message')
--- ```
---
---Note that the message will not be logged if the severity level corresponding to the called function is less than [`log.level`](doc://configuration_reference_log_level).
---
---@param s any
---@param ... any
function log.warn(s, ...) end

---Log a message with the info level.
---
---* A message can be a string.
---* A message may contain C-style format specifiers `%d` or `%s`. Example:
---* A message may be a scalar data type or a table. Example:
---
---The actual output will be a line in the log, containing:
---* The current timestamp
---* A module name
---* 'E', 'W', 'I', 'V' or 'D' depending on the called function.
---* `message`.
---
---**Example:**
---
--- ```lua
--- local log = require('log')
--- log.cfg { level = 'verbose' }
--- log.warn('Warning message')
--- log.info('Tarantool version: %s', box.info.version)
--- log.error({ 500, 'Internal error' })
--- log.debug('Debug message')
--- ```
---
---Note that the message will not be logged if the severity level corresponding to the called function is less than [`log.level`](doc://configuration_reference_log_level).
---
---@param s any
---@param ... any
function log.info(s, ...) end

---Log a message with the error level.
---
---* A message can be a string.
---* A message may contain C-style format specifiers `%d` or `%s`. Example:
---* A message may be a scalar data type or a table. Example:
---
---The actual output will be a line in the log, containing:
---* The current timestamp
---* A module name
---* 'E', 'W', 'I', 'V' or 'D' depending on the called function.
---* `message`.
---
---**Example:**
---
--- ```lua
--- local log = require('log')
--- log.cfg { level = 'verbose' }
--- log.warn('Warning message')
--- log.info('Tarantool version: %s', box.info.version)
--- log.error({ 500, 'Internal error' })
--- log.debug('Debug message')
--- ```
---
---Note that the message will not be logged if the severity level corresponding to the called function is less than [`log.level`](doc://configuration_reference_log_level).
---
---@param s any
---@param ... any
function log.error(s, ...) end

---Log a message with the verbose level.
---
---* A message can be a string.
---* A message may contain C-style format specifiers `%d` or `%s`. Example:
---* A message may be a scalar data type or a table. Example:
---
---The actual output will be a line in the log, containing:
---* The current timestamp
---* A module name
---* 'E', 'W', 'I', 'V' or 'D' depending on the called function.
---* `message`.
---
---**Example:**
---
--- ```lua
--- local log = require('log')
--- log.cfg { level = 'verbose' }
--- log.warn('Warning message')
--- log.info('Tarantool version: %s', box.info.version)
--- log.error({ 500, 'Internal error' })
--- log.debug('Debug message')
--- ```
---
---Note that the message will not be logged if the severity level corresponding to the called function is less than [`log.level`](doc://configuration_reference_log_level).
---
---@param s any
---@param ... any
function log.verbose(s, ...) end

---Log a message with the debug level.
---
---* A message can be a string.
---* A message may contain C-style format specifiers `%d` or `%s`. Example:
---* A message may be a scalar data type or a table. Example:
---
---The actual output will be a line in the log, containing:
---* The current timestamp
---* A module name
---* 'E', 'W', 'I', 'V' or 'D' depending on the called function.
---* `message`.
---
---**Example:**
---
--- ```lua
--- local log = require('log')
--- log.cfg { level = 'verbose' }
--- log.warn('Warning message')
--- log.info('Tarantool version: %s', box.info.version)
--- log.error({ 500, 'Internal error' })
--- log.debug('Debug message')
--- ```
---
---Note that the message will not be logged if the severity level corresponding to the called function is less than [`log.level`](doc://configuration_reference_log_level).
---
---@param s any
---@param ... any
function log.debug(s, ...) end

---Set log level.
---
---@param lvl? number|string a number (0-7) or a level name, e.g. 'verbose'
function log.level(lvl) end

---@class log.cfg
---@field level? number|string (default: 5) Log level: a number (0-7) or a level name ('fatal', 'syserror', 'error', 'crit', 'warn', 'info', 'verbose', 'debug'). Corresponds to the `log.level` configuration option.
---@field log? string Log output destination: a file path, a pipe, or a syslog URI. Corresponds to the `log.to` configuration option.
---@field nonblock? boolean If `true`, Tarantool does not block during logging when the destination is unavailable and drops messages instead. Corresponds to the `log.nonblock` configuration option.
---@field format? "plain"|"json" (default: 'plain') Log format. Corresponds to the `log.format` configuration option.
---@field modules? table<string, number|string> Per-module log levels. Corresponds to the `log.modules` configuration option.
---@overload fun(cfg: log.cfg)

---Configure logging options without initializing the box engine (or before it).
---
---*Since 2.11.0*
---
---**Example:**
---
--- ```lua
--- local log = require('log')
--- log.cfg { level = 'verbose', format = 'json' }
--- ```
---
---The current configuration values can be read back from the same table, e.g. `log.cfg.level`.
---
---@type log.cfg
log.cfg = {}

---Return the PID of a logger.
---
---You can use this PID to send a signal to a log rotation program, so it can rotate logs.
---
---@return number pid
function log.pid() end

---Rotate the log.
---
---For example, you need to call this function to continue logging after a log rotation program renames or moves a file with the latest logs.
---
function log.rotate() end

---Set the log format: 'plain' or 'json'.
---
---The JSON format is not supported when logging to syslog.
---
---@param format "plain"|"json"
function log.log_format(format) end

---Create a new logger with the specified name.
---
---*Since 2.11.0*
---
---You can configure a specific log level for a new logger using the `log.modules` configuration property.
---
---@param name string
---@return log
function log.new(name) end

return log
