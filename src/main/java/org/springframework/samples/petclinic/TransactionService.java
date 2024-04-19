package org.springframework.samples.petclinic;

import io.overledger.dlt.xbt.connector.common.GlobalConstants;
import io.overledger.dlt.xbt.connector.controller.dto.BitcoinFeeDetails;
import io.overledger.dlt.xbt.connector.controller.dto.Destination;
import io.overledger.dlt.xbt.connector.controller.dto.Fee;
import io.overledger.dlt.xbt.connector.controller.dto.Location;
import io.overledger.dlt.xbt.connector.controller.dto.Origin;
import io.overledger.dlt.xbt.connector.controller.dto.Payment;
import io.overledger.dlt.xbt.connector.controller.dto.Status;
import io.overledger.dlt.xbt.connector.controller.dto.Transaction;
import io.overledger.dlt.xbt.connector.controller.dto.request.ConnectorTransactionRequest;
import io.overledger.dlt.xbt.connector.controller.dto.response.ConnectorTransactionResponse;
import io.overledger.dlt.xbt.connector.controller.dto.response.ExecuteSearchUtxoResponse;
import io.overledger.dlt.xbt.connector.controller.dto.response.TechnologyFeeResponse;
import io.overledger.dlt.xbt.connector.controller.dto.response.TransactionResponse;
import io.overledger.dlt.xbt.connector.enums.TransactionStatus;
import io.overledger.dlt.xbt.connector.exception.BitcoinRPCException;
import io.overledger.dlt.xbt.connector.service.blockchain.BitcoinService;
import io.overledger.dlt.xbt.connector.service.model.rpc.BlockInfo;
import io.overledger.dlt.xbt.connector.service.model.rpc.RawTransactionInfo;
import io.overledger.dlt.xbt.connector.service.model.rpc.RawTransactionNativeData;
import io.overledger.dlt.xbt.connector.service.model.rpc.RawTransactionVinInfo;
import io.overledger.dlt.xbt.connector.service.model.rpc.RawTransactionVoutInfo;
import io.overledger.dlt.xbt.connector.service.model.rpc.TxOutInfo;
import io.overledger.dlt.xbt.connector.service.model.rpc.UtxoNativeData;
import io.overledger.dlt.xbt.connector.util.BTCUtils;
import io.overledger.dlt.xbt.connector.util.UtxoValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Sha256Hash;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    // TODO fix streams and nested ifs
    private static final String DLT = "Bitcoin";
    private static final String DLT_UNIT = "BTC";
    private static final String COINBASE = "coinbase";
    private static final String OP_RETURN = "OP_RETURN";
    private static final String PAYMENT_TYPE = "PAYMENT";
    private static final String PENDING_MESSAGE = "Transaction is pending.";
    private static final String PENDING_DESCRIPTION = "The transaction has been successfully broadcasted to the network. "
            + "The status will next be updated after a sufficient block number has been reached. "
            + "At this point we can be confident that the transaction will be successful or will have failed.";
    private static final String BITCOIN_CRYPTOCURRENCY_CODE = "BTC";
    private static final String URGENT = "urgent";
    private static final String FAST = "fast";
    private final BitcoinService bitcoinService;
    @Value("${bitcoin.network}")
    private String network;
    @Value("${successful-transaction.numberOfBlocks}")
    private Integer numberOfBlocksForSuccessfulTx;

    public ConnectorTransactionResponse createTransaction(final ConnectorTransactionRequest connectorTransactionRequest) {
        String txHash = getTransactionHash(connectorTransactionRequest.getSigned());
        Status status;

        try {
            txHash = bitcoinService.sendRawTransaction(connectorTransactionRequest.getSigned());
            log.debug("Successfully created transaction with hash: " + txHash);

            status = Status.transactionPending(Instant.now().getEpochSecond());
        } catch (BitcoinRPCException e) {
            log.error("Failed to create transaction due to: {}", e.getMessage());
            var response = e.getResponse();
            status = Status.builder()
                    .value(TransactionStatus.FAILED.name())
                    .code(TransactionStatus.FAILED.getCode())
                    .message(response.getMessage())
                    .description(response.getCode() + ": " + response.getMessage())
                    .timestamp(String.valueOf(Instant.now().getEpochSecond()))
                    .build();
        }

        return ConnectorTransactionResponse.builder()
                .type(PAYMENT_TYPE)
                .location(Location.builder().technology(DLT).network(network).build())
                .status(status)
                .transactionId(txHash)
                .build();
    }

    public TransactionResponse getTransactionCreatedByRawHash(final String txHex) {
        var rawTransaction = bitcoinService.getRawTransaction(txHex);
        var timestamp = rawTransaction.getBlockTime() != null
                ? bitcoinService.getBlock(rawTransaction.getBlockHash())
                .getTime()
                : null;
        return TransactionResponse.builder()
                .type(PAYMENT_TYPE)
                .timestamp(String.valueOf(timestamp))
                .location(Location.builder()
                        .technology(DLT)
                        .network(network)
                        .build())
                .status(getStatus(txHex, rawTransaction.getBlockHash(), timestamp))
                .transaction(populateTransactionResponse(rawTransaction))
                .build();
    }

    public ExecuteSearchUtxoResponse getUtxoState(final String txId, final String index) {
        var rawTransaction = bitcoinService.getRawTransaction(txId);
        UtxoValidator.validUtxoTrxIdAndVout(rawTransaction, index);
        var txOut = bitcoinService.getTxOut(txId, Long.parseLong(index));
        var timestamp = rawTransaction.getBlockTime() != null
                ? String.valueOf(bitcoinService.getBlock(rawTransaction.getBlockHash()).getTime())
                : null;
        return ExecuteSearchUtxoResponse.builder()
                .timestamp(timestamp)
                .location(Location.builder()
                        .technology(DLT)
                        .network(network)
                        .build())
                .status(getUtxoStatus(txOut, txId, index, rawTransaction, rawTransaction.getBlockHash()))
                .nativeData(populateUtxoNativeData(rawTransaction, index))
                .destination(populateUtxoDestinationDetails(rawTransaction, index))
                .utxoId(txId + ":" + index)
                .build();
    }

    public TechnologyFeeResponse estimateTransactionFee(final String urgency, final int originSize, final int destinationSize) {
        var blocks = StringUtils.equals(urgency, URGENT) ? 1 : StringUtils.equals(urgency, FAST) ? 3 : 6;
        var smartFeeEstimationInfo = bitcoinService.estimateSmartFee(blocks);
        var feeRate = BigDecimal.valueOf(smartFeeEstimationInfo.getFeerate());
        int transactionSizeBytes = (originSize * 180) + (destinationSize * 34) + 10 + originSize;

        // Adjusting the transaction size by 20% so the fee returned to the user is slightly higher
        // and his transaction is guaranteed to work
        int adjustedTransactionSizeBytes = transactionSizeBytes * 120 / 100;


        BitcoinFeeDetails feeDetails = BitcoinFeeDetails.builder()
                .type(GlobalConstants.TECHNOLOGY)
                .feeRate(feeRate)
                .transactionSize(adjustedTransactionSizeBytes)
                .build();

        BigDecimal dltTotalFeeInBitcoin = feeRate.multiply(new BigDecimal(adjustedTransactionSizeBytes)).divide(new BigDecimal("1000"));

        return TechnologyFeeResponse.builder()
                // TODO: for FiatFee and CurrencyCode, we need a conversion supplier
                .transactionFiatFee("")
                .fiatCurrencyCode("")
                .transactionDLTFee(dltTotalFeeInBitcoin)
                .transactionPaymentUnit(BITCOIN_CRYPTOCURRENCY_CODE)
                .urgency(urgency)
                .feeDetails(feeDetails)
                .build();
    }

    private String getTransactionHash(final String signedTransaction) {
        // In bitcoin, the data is hashed twice using Sha256 then reversed
        Sha256Hash hashedTwice = Sha256Hash.twiceOf(BTCUtils.parseAsHexOrBase58(signedTransaction));
        return Sha256Hash.wrap(hashedTwice.getReversedBytes()).toString();
    }

    private Status getStatus(final String txHash, final String blockHash, final Long timestamp) {
        if (blockHash != null) {
            RawTransactionInfo transaction = bitcoinService.getRawTransaction(txHash, blockHash);
            BlockInfo txBlock = bitcoinService.getBlock(transaction.getBlockHash());
            if (transaction.getConfirmations() > numberOfBlocksForSuccessfulTx) {
                String successfulBlockHash = bitcoinService.getBlockHash(txBlock.getHeight() + numberOfBlocksForSuccessfulTx);
                BlockInfo successfulBlockInfo = bitcoinService.getBlock(successfulBlockHash);
                return Status.transactionSuccessful(successfulBlockInfo.getTime());
            }
            return Status.transactionPending(timestamp);
        }
        RawTransactionInfo transaction = bitcoinService.getRawTransaction(txHash);
        return StringUtils.isEmpty(transaction.getHex())
                ? Status.transactionFailed(Instant.now().getEpochSecond())
                : Status.transactionPending(Instant.now().getEpochSecond());
    }

    // TODO fix, decompose
    @SuppressWarnings("checkstyle:methodlength")
    private Transaction populateTransactionResponse(final RawTransactionInfo rawTransaction) {
        NumberFormat df = new DecimalFormat("###.########################");

        List<Destination> destinationList = new ArrayList<>();
        List<Origin> originList = new ArrayList<>();
        BigDecimal totalOutputAmount = BigDecimal.ZERO;
        BigDecimal totalInputAmount = BigDecimal.ZERO;
        BigDecimal feeAmount = BigDecimal.ZERO;
        StringBuilder message = new StringBuilder();
        var outList = rawTransaction.getVout();
        var inList = rawTransaction.getVin();

        for (RawTransactionVoutInfo out : outList) {
            if (out.getScriptPubKey().getAsm().startsWith(OP_RETURN)) {
                String hex = out.getScriptPubKey().getHex().substring(4);
                try {
                    if (!message.toString().isEmpty()) {
                        message.append(" ");
                    }
                    message.append(BTCUtils.hexToUTF8(hex));
                } catch (DecoderException ex) {
                    log.error("Unable to decode OP_RETURN message" + ex.getMessage());
                }
            }

            destinationList.add(Destination.builder()
                    .destinationId(out.getScriptPubKey().getAddress() == null ? "" : out.getScriptPubKey().getAddress())
                    .payment(Payment.builder()
                            .amount(df.format(out.getValue()))
                            .unit(DLT_UNIT)
                            .build())
                    .build());
            totalOutputAmount = totalOutputAmount.add(out.getValue());
        }

        for (RawTransactionVinInfo in : inList) {
            Origin.OriginBuilder originBuilder = Origin.builder();
            if (in.getCoinbase() == null) {
                originBuilder.originId(in.getTransactionId() + ":" + in.getVout());

                RawTransactionInfo inTransaction = bitcoinService.getRawTransaction(in.getTransactionId());
                BigDecimal inAmount = inTransaction.getVout().get(in.getVout()).getValue();
                totalInputAmount = totalInputAmount.add(inAmount);
            } else {
                //Coinbase inputs don't have amounts or fees
                originBuilder.originId(COINBASE);
            }
            originList.add(originBuilder.build());
            if (totalInputAmount.compareTo(BigDecimal.ZERO) > 0) {
                feeAmount = totalInputAmount.subtract(totalOutputAmount);
            }
        }

        String messageStr = null;
        if (!message.toString().isEmpty()) {
            messageStr = message.toString();
        }

        Fee fee = null;
        if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
            fee = Fee.builder()
                    .amount(String.valueOf(feeAmount))
                    .unit(DLT_UNIT)
                    .build();
        }

        return Transaction.builder()
                .transactionId(rawTransaction.getTransactionId())
                .message(messageStr)
                .signed(rawTransaction.getHex())
                .totalPaymentAmount(Collections.singletonList(Payment.builder()
                        .amount(df.format(totalOutputAmount))
                        .unit(DLT_UNIT)
                        .build()))
                .origin(originList)
                .destination(destinationList)
                .fee(fee)
                .nativeData(createNativeData(bitcoinService.getDecodeRawTransaction(rawTransaction.getHex())))
                .build();
    }

    private RawTransactionNativeData createNativeData(final RawTransactionInfo rawTransactionInfo) {
        return RawTransactionNativeData.builder().rawTransactionInfo(rawTransactionInfo).rawTransactionInfo(rawTransactionInfo).build();
    }

    @SuppressWarnings("checkstyle:parameternumber")
    private Status getUtxoStatus(final TxOutInfo txOut,
                                 final String txHash,
                                 final String index,
                                 final RawTransactionInfo rawTransaction,
                                 final String blockHash) {
        RawTransactionInfo transaction = bitcoinService.getRawTransaction(txHash, rawTransaction.getBlockHash());
        if (blockHash == null) {
            return StringUtils.isNotEmpty(transaction.getHex())
                    ? Status.utxoUnspent(Instant.now().getEpochSecond(), false)
                    : null;
        }
        RawTransactionVoutInfo out = getVoutFromRawTransactionByIndex(rawTransaction, index);
        return getUtxoStatus(txOut, transaction, out);
    }

    private Status getUtxoStatus(final TxOutInfo txOut, final RawTransactionInfo transaction, final RawTransactionVoutInfo out) {
        var txBlock = bitcoinService.getBlock(transaction.getBlockHash());
        return transaction.getConfirmations() > numberOfBlocksForSuccessfulTx
                ? buildUtxoStatus(txOut, out, txBlock.getHeight() + numberOfBlocksForSuccessfulTx, true)
                : buildUtxoStatus(txOut, out, txBlock.getHeight(), false);
    }

    private Status buildUtxoStatus(final TxOutInfo txOut, final RawTransactionVoutInfo out, final long height, final boolean successful) {
        var blockHash = bitcoinService.getBlockHash(height);
        var blockTime = bitcoinService.getBlock(blockHash)
                .getTime();
        return isUnspendable(out)
                ? Status.utxoUnspendable(blockTime, successful)
                : txOut != null
                ? Status.utxoUnspent(blockTime, successful)
                : Status.utxoSpent(blockTime, successful);
    }

    private boolean isUnspendable(final RawTransactionVoutInfo out) {
        return out != null
                && out.getScriptPubKey() != null
                && StringUtils.startsWith(out.getScriptPubKey().getAsm(), OP_RETURN);
    }

    private RawTransactionVoutInfo getVoutFromRawTransactionByIndex(final RawTransactionInfo rawTransaction, final String index) {
        return CollectionUtils.emptyIfNull(rawTransaction.getVout())
                .stream()
                .filter(out -> String.valueOf(out.getIndex()).equals(index))
                .collect(BTCUtils.toSingleton());
    }

    private UtxoNativeData populateUtxoNativeData(final RawTransactionInfo rawTransaction, final String index) {
        List<RawTransactionVoutInfo> voutInfo = rawTransaction.getVout().stream()
                .filter(out -> String.valueOf(out.getIndex()).equals(index)).collect(Collectors.toList());
        return UtxoNativeData.builder().rawTransactionVoutInfos(voutInfo).build();
    }

    private List<Destination> populateUtxoDestinationDetails(final RawTransactionInfo rawTransaction, final String index) {
        NumberFormat df = new DecimalFormat("###.########################");
        List<RawTransactionVoutInfo> outList = rawTransaction.getVout();
        RawTransactionVoutInfo out = outList.get(Integer.parseInt(index));
        return List.of(Destination.builder()
                .destinationId(out.getScriptPubKey().getAddress() == null ? "" : out.getScriptPubKey().getAddress())
                .payment(Payment.builder()
                        .amount(df.format(out.getValue()))
                        .unit(DLT_UNIT)
                        .build())
                .build());
    }
}
