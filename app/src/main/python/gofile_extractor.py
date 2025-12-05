"""
GoFile Extractor for Android
Simplified version without Rich UI, focused on API calls
Based on: https://github.com/Lysagxra/GoFileDownloader
"""

import json
import requests
from typing import Optional, Dict, List, Tuple

GOFILE_API = "https://api.gofile.io"
GOFILE_API_ACCOUNTS = f"{GOFILE_API}/accounts"
USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"

_cached_token: Optional[str] = None


def create_account_token(max_retries: int = 5) -> Optional[str]:
    """Create a guest account and return the access token."""
    global _cached_token
    
    if _cached_token:
        return _cached_token
    
    headers = {
        "User-Agent": USER_AGENT,
        "Accept-Encoding": "gzip, deflate, br",
        "Accept": "*/*",
        "Connection": "keep-alive",
    }
    
    for attempt in range(max_retries):
        try:
            response = requests.post(
                GOFILE_API_ACCOUNTS,
                headers=headers,
                timeout=15
            )
            
            if response.status_code != 200:
                if attempt < max_retries - 1:
                    continue
                return None
            
            data = response.json()
            
            if data.get("status") == "ok":
                token = data.get("data", {}).get("token")
                if token:
                    _cached_token = token
                    return token
                    
        except Exception as e:
            if attempt < max_retries - 1:
                continue
            return None
    
    return None


def get_content_id(url: str) -> Optional[str]:
    """Extract content ID from GoFile URL."""
    try:
        parts = url.rstrip("/").split("/")
        if "d" not in parts:
            return None
        
        d_index = parts.index("d")
        if d_index != -1 and d_index + 1 < len(parts):
            return parts[d_index + 1].split("?")[0]
        
        return None
        
    except Exception:
        return None


def get_content_info(
    content_id: str,
    token: str,
    password: Optional[str] = None,
    max_retries: int = 5
) -> Optional[Dict]:
    """Get content information from GoFile API."""
    
    for attempt in range(max_retries):
        try:
            api_url = f"{GOFILE_API}/contents/{content_id}?wt=4fd6sg89d7s6&cache=true"
            
            if password:
                api_url += f"&password={password}"
            
            headers = {
                "User-Agent": USER_AGENT,
                "Accept-Encoding": "gzip, deflate, br",
                "Accept": "*/*",
                "Connection": "keep-alive",
                "Authorization": f"Bearer {token}",
                "Cookie": f"accountToken={token}"
            }
            
            response = requests.get(api_url, headers=headers, timeout=15)
            
            if response.status_code != 200:
                if attempt < max_retries - 1:
                    continue
                return None
            
            return response.json()
            
        except Exception:
            if attempt < max_retries - 1:
                continue
            return None
    
    return None


def extract_download_link(url: str, password: Optional[str] = None) -> Optional[Dict[str, any]]:
    """
    Extract direct download link from GoFile URL.
    Returns dict with 'url' and 'headers' or None if failed.
    """
    try:
        content_id = get_content_id(url)
        if not content_id:
            return None
        
        token = create_account_token()
        if not token:
            return None
        
        json_response = get_content_info(content_id, token, password)
        if not json_response or json_response.get("status") != "ok":
            return None
        
        data = json_response.get("data")
        if not data:
            return None
        
        if data.get("password") and data.get("passwordStatus") != "passwordOk":
            return None
        
        headers = {
            "User-Agent": USER_AGENT,
            "Accept-Encoding": "gzip, deflate, br",
            "Accept": "*/*",
            "Connection": "keep-alive",
            "Cookie": f"accountToken={token}"
        }
        
        content_type = data.get("type")
        
        if content_type == "file":
            direct_link = data.get("link", "")
            if direct_link:
                if not direct_link.startswith("http"):
                    direct_link = f"https:{direct_link}"
                headers["Referer"] = direct_link
                return {
                    "url": direct_link,
                    "headers": headers
                }
        
        elif content_type == "folder":
            children = data.get("children", {})
            if children:
                for child_id, child in children.items():
                    if child.get("type") == "file":
                        direct_link = child.get("link", "")
                        if direct_link:
                            if not direct_link.startswith("http"):
                                direct_link = f"https:{direct_link}"
                            headers["Referer"] = direct_link
                            return {
                                "url": direct_link,
                                "headers": headers
                            }
        
        return None
        
    except Exception:
        return None


def extract_with_retry(url: str, password: Optional[str] = None, max_retries: int = 3) -> Optional[str]:
    """
    Extract download link with retry logic.
    Returns JSON string with url and headers, or None if failed.
    """
    for attempt in range(max_retries):
        try:
            result = extract_download_link(url, password)
            if result:
                return json.dumps(result)
        except Exception:
            if attempt < max_retries - 1:
                continue
    
    return None
