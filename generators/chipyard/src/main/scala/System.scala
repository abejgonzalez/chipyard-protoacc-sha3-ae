//******************************************************************************
// Copyright (c) 2019 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package chipyard

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.{DontTouch}
import freechips.rocketchip.amba.axi4._

// ---------------------------------------------------------------------
// Base system that uses the debug test module (dtm) to bringup the core
// ---------------------------------------------------------------------

trait CanHaveMasterAXI4MemPortMaxFlight1 { this: BaseSubsystem =>
  private val memPortParamsOpt = p(ExtMem)
  private val portName = "axi4"
  private val device = new MemoryDevice
  private val idBits = memPortParamsOpt.map(_.master.idBits).getOrElse(1)
  val memAXI4Node = AXI4SlaveNode(memPortParamsOpt.map({ case MemoryPortParams(memPortParams, nMemoryChannels) =>
    Seq.tabulate(nMemoryChannels) { channel =>
      val base = AddressSet.misaligned(memPortParams.base, memPortParams.size)
      val filter = AddressSet(channel * mbus.blockBytes, ~((nMemoryChannels-1) * mbus.blockBytes))

      AXI4SlavePortParameters(
        slaves = Seq(AXI4SlaveParameters(
          address       = base.flatMap(_.intersect(filter)),
          resources     = device.reg,
          regionType    = RegionType.UNCACHED, // cacheable
          executable    = true,
          supportsWrite = TransferSizes(1, mbus.blockBytes),
          supportsRead  = TransferSizes(1, mbus.blockBytes),
          interleavedId = Some(0))), // slave does not interleave read responses
        beatBytes = memPortParams.beatBytes)
    }
  }).toList.flatten)

  mbus.coupleTo(s"memory_controller_port_named_$portName") {
    (memAXI4Node
      :*= AXI4UserYanker(Some(1))
      :*= AXI4IdIndexer(idBits)
      :*= TLToAXI4()
      :*= TLWidthWidget(mbus.beatBytes)
      :*= _)
  }

  val mem_axi4 = InModuleBody { memAXI4Node.makeIOs() }
}


/**
 * Base top with periphery devices and ports, and a BOOM + Rocket subsystem
 */
class System(implicit p: Parameters) extends Subsystem
  with HasAsyncExtInterrupts
  with CanHaveMasterAXI4MemPortMaxFlight1
  with CanHaveMasterAXI4MMIOPort
  with CanHaveSlaveAXI4Port
  with HasPeripheryBootROM
{
  override lazy val module = new SystemModule(this)
}

/**
 * Base top module implementation with periphery devices and ports, and a BOOM + Rocket subsystem
 */
class SystemModule[+L <: System](_outer: L) extends SubsystemModuleImp(_outer)
  with HasRTCModuleImp
  with HasExtInterruptsModuleImp
  with HasPeripheryBootROMModuleImp
  with DontTouch
