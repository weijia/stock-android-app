#!/usr/bin/env python3
"""
股票行情 HTTP 服务器
提供实时行情、K线、分时数据接口
"""

import json
import logging
import os
import sys
from datetime import datetime, timedelta
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("StockServer")

# 数据源配置
DATA_SOURCE = os.getenv("DATA_SOURCE", "mootdx")  # mootdx 或 tushare

# mootdx 数据源（通达信）
try:
    from mootdx.quotes import Quotes
    MOOTDX_AVAILABLE = True
    logger.info("mootdx 数据源可用")
except ImportError:
    MOOTDX_AVAILABLE = False
    logger.warning("mootdx 未安装，使用 pip install mootdx 安装")

# tushare 数据源
try:
    import tushare as ts
    TUSHARE_TOKEN = os.getenv("TUSHARE_TOKEN", "")
    if TUSHARE_TOKEN:
        ts.set_token(TUSHARE_TOKEN)
        pro = ts.pro_api()
        TUSHARE_AVAILABLE = True
        logger.info("tushare 数据源可用")
    else:
        TUSHARE_AVAILABLE = False
        logger.warning("tushare token 未配置")
except ImportError:
    TUSHARE_AVAILABLE = False
    logger.warning("tushare 未安装")


def _df_to_records(df):
    """将 DataFrame 转换为记录列表"""
    if df is None or df.empty:
        return []
    
    # 标准化列名
    column_map = {
        'date': 'date',
        'open': 'open',
        'high': 'high',
        'low': 'low',
        'close': 'close',
        'volume': 'volume',
        'amount': 'amount',
        'turn': 'turn',
        'datetime': 'date',
        'time': 'time',
    }
    
    records = []
    for _, row in df.iterrows():
        record = {}
        for col in df.columns:
            key = column_map.get(col.lower(), col.lower())
            value = row[col]
            
            # 处理时间格式
            if key == 'date' and hasattr(value, 'strftime'):
                value = value.strftime('%Y-%m-%d')
            elif key == 'time' and hasattr(value, 'strftime'):
                value = value.strftime('%H:%M')
            
            # 处理数值
            if isinstance(value, (int, float)):
                if 'volume' in key or 'amount' in key:
                    record[key] = float(value)
                else:
                    record[key] = float(value)
            else:
                record[key] = str(value) if value is not None else ''
        
        records.append(record)
    
    return records


class MootdxProvider:
    """mootdx 数据源"""
    
    def __init__(self):
        self.client = Quotes.factory(market='std')
    
    def fetch_realtime(self, code):
        """获取实时行情
        
        API: client.quotes(symbol=["000001"])
        """
        try:
            # 使用 quotes 获取实时行情
            df = self.client.quotes(symbol=[code])
            
            if df is None or df.empty:
                return None
            
            # 标准化数据
            row = df.iloc[0]
            data = {
                'name': row.get('name', ''),
                'price': float(row.get('price', 0)),
                'last_close': float(row.get('last_close', 0)),
                'open': float(row.get('open', 0)),
                'high': float(row.get('high', 0)),
                'low': float(row.get('low', 0)),
                'volume': float(row.get('volume', 0)),
                'amount': float(row.get('amount', 0)),
            }
            
            # 计算涨跌幅
            if data['last_close'] > 0:
                data['change_amt'] = data['price'] - data['last_close']
                data['change_pct'] = (data['change_amt'] / data['last_close']) * 100
            else:
                data['change_amt'] = 0
                data['change_pct'] = 0
            
            return data
        except Exception as e:
            logger.error(f"mootdx 实时行情获取失败: {e}")
            return None
    
    def fetch_kline(self, code, days=30):
        """获取 K 线数据
        
        API: client.bars(symbol='600036', frequency=9)
        frequency: 9=日线, 7=1分钟, 8=1分钟
        """
        try:
            # 获取日线数据
            df = self.client.bars(
                symbol=code,
                frequency=9,  # 日线
                offset=days
            )
            
            if df is None or df.empty:
                return None
            
            return _df_to_records(df)
        except Exception as e:
            logger.error(f"mootdx K线获取失败: {e}")
            return None
    
    def fetch_minutes(self, code, date=None):
        """获取历史分时数据
        
        API: client.minutes(symbol='000001', date='20171010')
        
        Args:
            code: 股票代码
            date: 日期字符串，格式 YYYYMMDD，如 '20171010'
                  如果不指定，获取最新交易日
        
        Returns:
            DataFrame, error
        """
        try:
            if date is None:
                # 获取最新交易日
                # 先尝试今天
                today = datetime.now().strftime('%Y%m%d')
                df = self.client.minutes(symbol=code, date=today)
                
                if df is None or df.empty:
                    # 尝试上一个交易日
                    yesterday = (datetime.now() - timedelta(days=1)).strftime('%Y%m%d')
                    df = self.client.minutes(symbol=code, date=yesterday)
                    
                    if df is None or df.empty:
                        # 尝试前两天
                        prev_day = (datetime.now() - timedelta(days=2)).strftime('%Y%m%d')
                        df = self.client.minutes(symbol=code, date=prev_day)
            
            else:
                df = self.client.minutes(symbol=code, date=date)
            
            if df is None or df.empty:
                return None, "无分时数据"
            
            return df, None
        except Exception as e:
            return None, str(e)


class TushareProvider:
    """tushare 数据源"""
    
    def fetch_realtime(self, code):
        """获取实时行情"""
        try:
            df = pro.daily(trade_date=datetime.now().strftime('%Y%m%d'), ts_code=f'{code}.SH')
            if df.empty:
                df = pro.daily(trade_date=datetime.now().strftime('%Y%m%d'), ts_code=f'{code}.SZ')
            
            if df.empty:
                return None
            
            row = df.iloc[0]
            return {
                'name': '',
                'price': float(row['close']),
                'last_close': float(row['pre_close']),
                'open': float(row['open']),
                'high': float(row['high']),
                'low': float(row['low']),
                'volume': float(row['vol']),
                'amount': float(row['amount']),
                'change_amt': float(row['close'] - row['pre_close']),
                'change_pct': float((row['close'] - row['pre_close']) / row['pre_close'] * 100),
            }
        except Exception as e:
            logger.error(f"tushare 实时行情获取失败: {e}")
            return None
    
    def fetch_kline(self, code, days=30):
        """获取 K 线数据"""
        try:
            end_date = datetime.now().strftime('%Y%m%d')
            start_date = (datetime.now() - timedelta(days=days*2)).strftime('%Y%m%d')
            
            df = pro.daily(
                ts_code=f'{code}.SH',
                start_date=start_date,
                end_date=end_date
            )
            if df.empty:
                df = pro.daily(
                    ts_code=f'{code}.SZ',
                    start_date=start_date,
                    end_date=end_date
                )
            
            if df.empty:
                return None
            
            records = []
            for _, row in df.iterrows():
                records.append({
                    'date': row['trade_date'],
                    'open': float(row['open']),
                    'high': float(row['high']),
                    'low': float(row['low']),
                    'close': float(row['close']),
                    'volume': float(row['vol']),
                })
            
            return records[-days:]
        except Exception as e:
            logger.error(f"tushare K线获取失败: {e}")
            return None


class StockPriceHandler(BaseHTTPRequestHandler):
    """股票行情请求处理器"""
    
    # 数据源实例
    mootdx_provider = MootdxProvider() if MOOTDX_AVAILABLE else None
    tushare_provider = TushareProvider() if TUSHARE_AVAILABLE else None
    
    def log_message(self, format, *args):
        """自定义日志格式"""
        logger.info("%s - %s", self.address_string(), format % args)
    
    def _send_json(self, code, data):
        """发送 JSON 响应"""
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode('utf-8'))
    
    def _send_error(self, code, message):
        """发送错误响应"""
        self._send_json(code, {'code': code, 'error': message})
    
    def do_GET(self):
        """处理 GET 请求"""
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)
        
        logger.info(f"GET {path}")
        
        try:
            # 健康检查
            if path == '/api/health':
                self._handle_health()
            
            # 实时行情
            elif path.startswith('/api/realtime/'):
                code = path.split('/')[-1]
                self._handle_realtime(code)
            
            # K 线数据
            elif path.startswith('/api/kline/'):
                code = path.split('/')[-1]
                days = int(query.get('days', ['30'])[0])
                self._handle_kline(code, days)
            
            # 分时数据
            elif path.startswith('/api/intraday/'):
                code = path.split('/')[-1]
                date = query.get('date', [None])[0]  # 支持 date 参数
                self._handle_intraday(code, date)
            
            else:
                self._send_error(404, "接口不存在")
        
        except Exception as e:
            logger.error(f"请求处理异常: {e}")
            self._send_error(500, str(e))
    
    def _handle_health(self):
        """健康检查"""
        self._send_json(200, {
            'code': 200,
            'data': {'status': 'ok'},
            'timestamp': datetime.now().isoformat()
        })
    
    def _handle_realtime(self, code):
        """实时行情"""
        if not code:
            return self._send_error(400, "缺少股票代码")
        
        data = None
        
        # 使用 mootdx
        if self.mootdx_provider:
            data = self.mootdx_provider.fetch_realtime(code)
        
        # 使用 tushare 作为备选
        if data is None and self.tushare_provider:
            data = self.tushare_provider.fetch_realtime(code)
        
        if data:
            self._send_json(200, {
                'code': 200,
                'data': data,
                'timestamp': datetime.now().isoformat()
            })
        else:
            self._send_error(503, "无可用数据源")
    
    def _handle_kline(self, code, days):
        """K 线数据"""
        if not code:
            return self._send_error(400, "缺少股票代码")
        
        records = None
        
        if self.mootdx_provider:
            records = self.mootdx_provider.fetch_kline(code, days)
        
        if records is None and self.tushare_provider:
            records = self.tushare_provider.fetch_kline(code, days)
        
        if records:
            self._send_json(200, {
                'code': 200,
                'data': {
                    'code': code,
                    'count': len(records),
                    'data': records
                }
            })
        else:
            self._send_error(503, "无可用数据源")
    
    def _handle_intraday(self, code, date=None):
        """分时数据
        
        获取当天或指定日期的分时数据
        
        参数：
        - code: 股票代码（路径中）
        - date: 日期参数（可选），格式 YYYYMMDD
        
        返回：
        - code: 股票代码
        - date: 数据日期
        - count: 数据条数
        - pre_close: 昨收价
        - data: [{time, price, volume, avg_price}, ...] 按时间升序
        """
        if not code:
            return self._send_error(400, "缺少股票代码")
        
        # 使用 mootdx 获取分时数据
        if self.mootdx_provider:
            try:
                # 使用 minutes 接口获取历史分时数据
                df, error = self.mootdx_provider.fetch_minutes(code, date)
                
                if df is not None and not df.empty:
                    # 标准化数据
                    records = _df_to_records(df)
                    
                    # 计算均价
                    total_amount = 0
                    total_volume = 0
                    for r in records:
                        vol = r.get('volume', 0) or 0
                        price = r.get('close', 0) or 0
                        total_volume += vol
                        total_amount += vol * price
                    
                    avg_price = total_amount / total_volume if total_volume > 0 else 0
                    
                    # 获取昨收价（从实时数据）
                    pre_close = None
                    realtime_data = self.mootdx_provider.fetch_realtime(code)
                    if realtime_data:
                        pre_close = realtime_data.get('last_close')
                    
                    # 获取数据日期
                    data_date = records[0].get('date', '') if records else ''
                    if isinstance(data_date, str) and len(data_date) >= 10:
                        data_date = data_date[:10]
                    
                    # 格式化分时数据
                    intraday_data = []
                    for r in records:
                        time_str = r.get('date', '')
                        if isinstance(time_str, str):
                            # 提取时间部分
                            if 'T' in time_str:
                                time_str = time_str.split('T')[1][:5]  # HH:MM
                            elif len(time_str) > 10:
                                time_str = time_str[11:16]
                        
                        intraday_data.append({
                            'time': time_str,
                            'price': r.get('close'),
                            'volume': r.get('volume'),
                            'avg_price': round(avg_price, 2)
                        })
                    
                    self._send_json(200, {
                        'code': 200,
                        'stock_code': code,  # 使用 stock_code 避免与响应状态码冲突
                        'date': data_date,
                        'count': len(intraday_data),
                        'pre_close': pre_close,
                        'data': intraday_data
                    })
                    return
                else:
                    logger.warning(f"mootdx分时数据获取失败: {error}")
            except Exception as e:
                logger.error(f"分时数据处理异常: {e}")
        
        self._send_error(503, "分时数据不可用，需要安装 mootdx 并确保通达信服务器可访问")


def main():
    """主函数"""
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", 8080))
    
    server = HTTPServer((host, port), StockPriceHandler)
    logger.info(f"股票行情服务器启动: http://{host}:{port}")
    logger.info(f"数据源: {DATA_SOURCE}")
    logger.info(f"接口列表:")
    logger.info("  - /api/health - 健康检查")
    logger.info("  - /api/realtime/<code> - 实时行情（示例：000001）")
    logger.info("  - /api/kline/<code>?days=30 - K线数据（示例：000001）")
    logger.info("  - /api/intraday/<code>?date=YYYYMMDD - 分时数据（示例：000001）")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("服务器停止")
        server.shutdown()


if __name__ == '__main__':
    main()