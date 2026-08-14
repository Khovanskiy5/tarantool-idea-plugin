---@meta

---# Builtin `box.ctl` submodule
---
---The `wait_ro` (wait until read-only) and `wait_rw` (wait until read-write) functions are useful during server initialization.
---
---To see whether a function is already in read-only or read-write mode, check :ref:`box.info.ro <box_introspection-box_info>`.
---
---A particular use is for :doc:`box.once() </reference/reference_lua/box_once>`.
---
---For example, when a replica is initializing, it may call a `box.once()` function while the server is still in read-only mode, and fail to make changes that are necessary only once before the replica is fully initialized.
---
---This could cause conflicts between a master and a replica if the master is in read-write mode and the replica is in read-only mode. Waiting until "read only mode = false" solves this problem.
box.ctl = {}

---Wait, then choose new replication leader.
---
---*Since 2.6.2*
---*Renamed in 2.6.3*
---
---For [synchronous transactions](doc://repl_sync) it is possible that a new leader will be chosen but the transactions of the old leader have not been completed. Therefore to finalize the transaction, the function `box.ctl.promote()` should be called, as mentioned in the notes for
---[leader election](doc://repl_leader_elect_important).
---
---The old name for this function is `box.ctl.clear_synchro_queue()`.
---
---The [election state](lua://box.info.election) should change to `leader`.
function box.ctl.promote() end

---Revoke the leader role from the instance.
---
---*Since 2.10.0*
---
---On [synchronous transaction queue owner](lua://box.info.synchro), the function works in the following way:
---* If [`box.cfg.election_mode`](doc://cfg_replication-election_mode) is `off`, the function writes a `DEMOTE` request to WAL. The `DEMOTE` request clears the ownership of the synchronous transaction queue, while the `PROMOTE` request assigns it to a new instance.
---* If [`box.cfg.election_mode`](doc://cfg_replication-election_mode) is enabled in any mode, then the function makes the instance start a new term and give up the leader role.
---
---On instances that are not queue owners, the function does nothing and returns immediately.
function box.ctl.demote() end

---Wait until `box.info.ro` is false.
---
---**Example:**
---
--- ```tarantoolsession
--- tarantool> n = box.ctl.wait_ro(0.1)
--- ---
--- ...
--- ```
---
---@async
---@param timeout? number
function box.ctl.wait_rw(timeout) end

---Wait until `box.info.ro` is true.
---
---**Example:**
---
--- ```tarantoolsession
--- tarantool> box.info().ro
--- ---
--- - false
--- ...
---
--- tarantool> n = box.ctl.wait_ro(0.1)
--- ---
--- - error: timed out
--- ...
--- ```
---
---@async
---@param timeout? number
function box.ctl.wait_ro(timeout) end

---Create a shutdown trigger.
---
---The `trigger-function` will be executed whenever [`os.exit()`](os.exit) happens, or when the server is shut down after receiving a `SIGTERM` or `SIGINT` or `SIGHUP` signal (but not after `SIGSEGV` or `SIGABORT` or any signal that causes immediate program termination).
---
---If the parameters are (nil, old-trigger-function), then the old trigger is deleted.
---
---If you want to set a timeout for this trigger, use the [`set_on_shutdown_timeout`](lua://box.ctl.on_shutdown_timeout) function.
---
---@param trigger_function fun()
---@param old_trigger_function? fun()
---@return fun()? created_trigger
function box.ctl.on_shutdown(trigger_function, old_trigger_function) end

---Create a trigger executed when leader election changes replica state.
---
---* Since 2.10.0 *
---
---Create a [trigger](doc://triggers) executed every time the current state of a replica set node in regard to [leader election](doc://repl_leader_elect) changes.
---
---The current state is available in the [`box.info.election`](lua://box.info.election) table.
---
---The trigger doesn't accept any parameters.
---
---You can see the changes in `box.info.election` and [`box.info.synchro`](box.info.synchro).
---
---@param trigger function
function box.ctl.on_election(trigger) end

---Create a "schema_init trigger".
---
---*Since 1.10.0*
---
---The `trigger-function` will be executed when [`box.cfg{}`](lua://box.cfg) happens for the first time. That is, the `schema_init` trigger is called before the server's configuration and recovery begins, and therefore `box.ctl.on_schema_init` must be called before `box.cfg` is called.
---
---If the parameters are (nil, old-trigger-function), then the old trigger is deleted.
---
---A common use is: make a `schema_init` trigger function which creates a `before_replace` trigger function on a system space. Thus, since system spaces are created when the server starts, the `before_replace` triggers will be enabled when the system spaces are created.
---
---@param trigger_function fun()
---@param old_trigger_function? fun()
---@return fun()? created_trigger
function box.ctl.on_schema_init(trigger_function, old_trigger_function) end

---Set a timeout for the `on_shutdown` trigger.
---
---*Since 2.8.1*
---
---If the timeout has expired, the server stops immediately regardless of whether any `on_shutdown` triggers are left unexecuted.
---
---@param timeout number timeout in seconds (default: 3 seconds)
function box.ctl.set_on_shutdown_timeout(timeout) end

---@alias box.ctl.recovery_state
---| 'snapshot_recovered' # the node has recovered the snapshot files
---| 'wal_recovered' # the node has recovered the WAL files
---| 'indexes_built' # the node has built secondary indexes for memtx spaces
---| 'synced' # the node has synced with enough remote peers to leave the orphan mode

---Create a trigger executed on different stages of a node recovery or initial configuration.
---
---*Since 2.11.0*
---
---Note that you need to set the trigger before the initial `box.cfg` call to make it work for all the recovery stages.
---
---The trigger function accepts one parameter -- a string that shows the current recovery state.
---
---A registered trigger is executed on each of the supported recovery states. Note that the `synced` state might be reached long after the instance starts accepting requests.
---
---@param trigger_function fun(state: box.ctl.recovery_state)
---@return fun()? created_trigger
function box.ctl.on_recovery_state(trigger_function) end

---Check if recovery has finished.
---
---*Since 2.5.3*
---
---Until recovery has finished, space changes such as `insert` or `update` are not possible.
---
---@return boolean finished `true` if recovery has finished, otherwise `false`
function box.ctl.is_recovery_finished() end

---Make the instance a bootstrap leader of a replica set.
---
---*Since 3.0.0*
---
---To be able to make the instance a bootstrap leader manually, the [`replication.bootstrap_strategy`](doc://configuration_reference_replication_bootstrap_strategy) configuration option should be set to `supervised`. In this case, the instances do not choose a bootstrap leader automatically but wait for it to be appointed manually.
---
---**Note:** configuration fails if no bootstrap leader is appointed during a `replication.connect_timeout`.
---
---@param opts? { graceful?: boolean } *since 3.4.0*: `graceful = true` checks for existing bootstrap leaders before assuming the role
function box.ctl.make_bootstrap_leader(opts) end
