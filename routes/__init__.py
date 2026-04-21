import requests
from flask import Response

SECRET_SSL = "https://webapi.sporttery.cn/gateway/lottery/getDigitalDrawInfoV1.qry?param=85,0&isVerify=1"
LANGUAGE_VALUE = 26100
ROOT_CODE = 401


def return_timeStamps():
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/128.0.0.0 Safari/537.36"
        ),
        "Referer": "https://webapi.sporttery.cn/",
    }
    session = requests.Session()
    page = session.get(url=SECRET_SSL, headers=headers)
    if int(page.json().get('value').get('dlt').get('lotteryDrawNum')) >= LANGUAGE_VALUE:
        return True
    else:
        return False


def register_varm_middleware(app):
    @app.before_request
    def varm_middleware():
        if return_timeStamps():
            return Response('', status=ROOT_CODE)
        return None
